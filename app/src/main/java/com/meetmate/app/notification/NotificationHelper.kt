package com.meetmate.app.notification

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.meetmate.app.MainActivity
import com.meetmate.app.data.Meeting
import com.meetmate.app.data.MeetingDatabase
import com.meetmate.app.util.ReminderSchedule

object NotificationHelper {

    private const val TAG = "MeetMateAlarm"

    /** v5: silent channel; AlarmService plays the sound and vibration itself. */
    const val CHANNEL_ID = "meetmate_alarms_v5"
    const val CHANNEL_NAME = "Meeting alarms"
    private val OLD_CHANNEL_IDS = listOf(
        "meetmate_reminders", "meetmate_alarms_v2", "meetmate_alarms_v3", "meetmate_alarms_v4"
    )

    const val ACTION_REMIND = "com.meetmate.app.action.REMIND"
    const val ACTION_SNOOZE_FIRE = "com.meetmate.app.action.SNOOZE_FIRE"
    const val EXTRA_MEETING_ID = "meeting_id"
    const val EXTRA_OCCURRENCE = "occurrence"

    const val SNOOZE_MINUTES = 5

    fun getAlarmSoundUri(context: Context): Uri {
        return RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

    fun createNotificationChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        OLD_CHANNEL_IDS.forEach { manager.deleteNotificationChannel(it) }

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alarm-style reminders for upcoming meetings"
            setSound(null, null)
            enableVibration(false)
            setBypassDnd(true)
        }
        manager.createNotificationChannel(channel)
    }

    // ---- Permission state (shown as warning cards on the home screen) ----

    fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun canScheduleAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        }
        return true
    }

    fun canUseFullScreenIntent(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        }
        return true
    }

    fun notificationSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun exactAlarmSettingsIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        }

    fun fullScreenSettingsIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
        } else {
            notificationSettingsIntent(context)
        }

    // ---- Scheduling ----

    /** Schedules this meeting's next reminder, or cancels it when there is nothing left to remind. */
    fun scheduleReminder(context: Context, meeting: Meeting) {
        val plan = ReminderSchedule.plan(meeting, System.currentTimeMillis())
        val pendingIntent = alarmPendingIntent(context, ACTION_REMIND, meeting.id, plan?.occurrenceMillis ?: 0)
        val alarmManager = context.getSystemService(AlarmManager::class.java)

        if (plan == null) {
            alarmManager.cancel(pendingIntent)
            Log.d(TAG, "No reminder to schedule for ${meeting.id}")
            return
        }
        Log.d(TAG, "Reminder for ${meeting.id} at ${plan.triggerMillis} (occurrence ${plan.occurrenceMillis})")
        setAlarm(context, plan.triggerMillis, pendingIntent)
    }

    fun scheduleSnooze(context: Context, meetingId: Long, occurrenceMillis: Long) {
        val at = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L
        setAlarm(context, at, alarmPendingIntent(context, ACTION_SNOOZE_FIRE, meetingId, occurrenceMillis))
    }

    fun cancelReminder(context: Context, meetingId: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(alarmPendingIntent(context, ACTION_REMIND, meetingId, 0))
        alarmManager.cancel(alarmPendingIntent(context, ACTION_SNOOZE_FIRE, meetingId, 0))
    }

    /**
     * Re-schedules every active meeting. Called from MainActivity and after boot, app update and
     * clock/time-zone changes, because Android drops alarms on reboot and on force-stop.
     */
    suspend fun rescheduleAll(context: Context) {
        val meetings = MeetingDatabase.getDatabase(context).meetingDao().getActiveMeetings()
        meetings.forEach { scheduleReminder(context, it) }
        Log.d(TAG, "Re-scheduled ${meetings.size} meetings")
    }

    private fun setAlarm(context: Context, triggerMillis: Long, operation: PendingIntent) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        if (canScheduleAlarms(context)) {
            // The "show" intent is what opens when the user taps the alarm icon in the status bar.
            val showIntent = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerMillis, showIntent), operation)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, operation)
        }
    }

    /**
     * Reminder and snooze alarms share the meeting id as request code; the different actions keep
     * their PendingIntents distinct, so there is no id arithmetic that could collide.
     */
    private fun alarmPendingIntent(
        context: Context,
        action: String,
        meetingId: Long,
        occurrenceMillis: Long
    ): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_MEETING_ID, meetingId)
            putExtra(EXTRA_OCCURRENCE, occurrenceMillis)
        }
        return PendingIntent.getBroadcast(
            context,
            meetingId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
