package com.meetmate.app.notification

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.meetmate.app.AlarmActivity
import com.meetmate.app.MainActivity
import com.meetmate.app.R
import com.meetmate.app.data.Meeting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** What is ringing right now; [AlarmActivity] observes this and closes when it becomes null. */
data class RingingAlarm(
    val meetingId: Long,
    val occurrenceMillis: Long,
    val title: String,
    val personName: String,
    val meetingLink: String,
    val notes: String,
    val snoozed: Boolean
)

/**
 * Plays the alarm sound and vibration in a foreground service, the way clock apps do, so Android
 * can't kill the process mid-ring. Owns the reminder notification and its Snooze / Dismiss actions.
 */
class AlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable { onTimeout() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> intent.toRingingAlarm()?.let { startRinging(it) } ?: stopSelf()
            ACTION_DISMISS -> finish(cancelNotification = true)
            ACTION_SNOOZE -> {
                _ringing.value?.let {
                    NotificationHelper.scheduleSnooze(this, it.meetingId, it.occurrenceMillis)
                }
                finish(cancelNotification = true)
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startRinging(alarm: RingingAlarm) {
        // A new alarm replaces whatever was ringing.
        stopSound()
        _ringing.value?.let { NotificationManagerCompat.from(this).cancel(notificationId(it.meetingId)) }
        _ringing.value = alarm

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else 0
        ServiceCompat.startForeground(this, notificationId(alarm.meetingId), buildNotification(alarm, ringing = true), type)

        playSound()
        startVibration()
        handler.removeCallbacks(timeoutRunnable)
        handler.postDelayed(timeoutRunnable, RING_TIMEOUT_MILLIS)
    }

    /** Nobody answered: go quiet but leave a normal notification so the reminder isn't lost. */
    private fun onTimeout() {
        val alarm = _ringing.value
        stopSound()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        if (alarm != null && NotificationHelper.canPostNotifications(this)) {
            try {
                NotificationManagerCompat.from(this)
                    .notify(notificationId(alarm.meetingId), buildNotification(alarm, ringing = false))
            } catch (e: SecurityException) {
                Log.w(TAG, "Couldn't post missed-reminder notification", e)
            }
        }
        _ringing.value = null
        stopSelf()
    }

    private fun finish(cancelNotification: Boolean) {
        val alarm = _ringing.value
        stopSound()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        if (cancelNotification && alarm != null) {
            NotificationManagerCompat.from(this).cancel(notificationId(alarm.meetingId))
        }
        _ringing.value = null
        stopSelf()
    }

    override fun onDestroy() {
        stopSound()
        _ringing.value = null
        super.onDestroy()
    }

    private fun playSound() {
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmService, NotificationHelper.getAlarmSoundUri(this@AlarmService))
                setWakeMode(this@AlarmService, PowerManager.PARTIAL_WAKE_LOCK)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play alarm sound", e)
        }
    }

    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        val pattern = longArrayOf(0, 600, 400, 600, 1200)
        vibrator?.vibrate(
            VibrationEffect.createWaveform(pattern, 0),
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
        )
    }

    private fun stopSound() {
        handler.removeCallbacks(timeoutRunnable)
        mediaPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
                it.release()
            } catch (_: Exception) {
            }
        }
        mediaPlayer = null
        vibrator?.cancel()
        vibrator = null
    }

    private fun buildNotification(alarm: RingingAlarm, ringing: Boolean): Notification {
        val id = notificationId(alarm.meetingId)
        val time = Instant.ofEpochMilli(alarm.occurrenceMillis).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("h:mm a"))
        val text = buildString {
            append(if (alarm.occurrenceMillis <= System.currentTimeMillis()) "Starting now" else "Starts at $time")
            if (alarm.personName.isNotBlank()) append(" · with ${alarm.personName}")
        }

        // Tapping the notification opens the app and stops the ringing.
        val openIntent = PendingIntent.getActivity(
            this, id,
            Intent(this, MainActivity::class.java)
                .setAction(MainActivity.ACTION_STOP_ALARM)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(alarm.title)
            .setContentText(text)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    if (alarm.notes.isBlank()) text else "$text\n${alarm.notes}"
                )
            )
            .setColor(ContextCompat.getColor(this, R.color.brand_iris))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openIntent)
            .setAutoCancel(!ringing)
            .setSilent(true)

        if (ringing) {
            val fullScreen = PendingIntent.getActivity(
                this, id,
                Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setOngoing(true)
                .setFullScreenIntent(fullScreen, true)
                .setDeleteIntent(servicePendingIntent(ACTION_DISMISS, id))
                .addAction(0, "Snooze ${NotificationHelper.SNOOZE_MINUTES} min", servicePendingIntent(ACTION_SNOOZE, id))
                .addAction(0, "Dismiss", servicePendingIntent(ACTION_DISMISS, id))
        } else {
            builder.setPriority(NotificationCompat.PRIORITY_DEFAULT)
        }

        if (alarm.meetingLink.isNotBlank()) {
            // Must be an Activity PendingIntent: services can't open other apps from a notification tap.
            val join = PendingIntent.getActivity(
                this, id,
                Intent(this, AlarmActivity::class.java)
                    .setAction(AlarmActivity.ACTION_JOIN)
                    .putExtra(AlarmActivity.EXTRA_LINK, alarm.meetingLink)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "Join", join)
        }
        return builder.build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this, requestCode,
            Intent(this, AlarmService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun Intent.toRingingAlarm(): RingingAlarm? {
        val meetingId = getLongExtra(EXTRA_MEETING_ID, -1)
        if (meetingId < 0) return null
        return RingingAlarm(
            meetingId = meetingId,
            occurrenceMillis = getLongExtra(EXTRA_OCCURRENCE, 0),
            title = getStringExtra(EXTRA_TITLE) ?: "Meeting",
            personName = getStringExtra(EXTRA_PERSON).orEmpty(),
            meetingLink = getStringExtra(EXTRA_LINK).orEmpty(),
            notes = getStringExtra(EXTRA_NOTES).orEmpty(),
            snoozed = getBooleanExtra(EXTRA_SNOOZED, false)
        )
    }

    companion object {
        private const val TAG = "MeetMateAlarm"
        private const val RING_TIMEOUT_MILLIS = 60_000L
        private const val NOTIFICATION_ID_BASE = 1000

        private const val ACTION_START = "com.meetmate.app.action.ALARM_START"
        private const val ACTION_DISMISS = "com.meetmate.app.action.ALARM_DISMISS"
        private const val ACTION_SNOOZE = "com.meetmate.app.action.ALARM_SNOOZE"
        private const val EXTRA_MEETING_ID = "meeting_id"
        private const val EXTRA_OCCURRENCE = "occurrence"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_PERSON = "person"
        private const val EXTRA_LINK = "link"
        private const val EXTRA_NOTES = "notes"
        private const val EXTRA_SNOOZED = "snoozed"

        private val _ringing = MutableStateFlow<RingingAlarm?>(null)
        val ringing: StateFlow<RingingAlarm?> = _ringing

        private fun notificationId(meetingId: Long) = NOTIFICATION_ID_BASE + meetingId.toInt()

        fun start(context: Context, meeting: Meeting, occurrenceMillis: Long, snoozed: Boolean) {
            val intent = Intent(context, AlarmService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MEETING_ID, meeting.id)
                putExtra(EXTRA_OCCURRENCE, occurrenceMillis)
                putExtra(EXTRA_TITLE, meeting.title)
                putExtra(EXTRA_PERSON, meeting.personName)
                putExtra(EXTRA_LINK, meeting.meetingLink)
                putExtra(EXTRA_NOTES, meeting.notes)
                putExtra(EXTRA_SNOOZED, snoozed)
            }
            // Allowed from the background because it's started by an exact alarm.
            ContextCompat.startForegroundService(context, intent)
        }

        /** Stops the ringing if anything is ringing; used when the app or alarm screen is opened. */
        fun dismiss(context: Context) {
            if (_ringing.value == null) return
            context.startService(Intent(context, AlarmService::class.java).setAction(ACTION_DISMISS))
        }

        fun snooze(context: Context) {
            if (_ringing.value == null) return
            context.startService(Intent(context, AlarmService::class.java).setAction(ACTION_SNOOZE))
        }
    }
}
