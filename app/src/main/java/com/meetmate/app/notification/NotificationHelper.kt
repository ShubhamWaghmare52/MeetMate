package com.meetmate.app.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.meetmate.app.data.Meeting

object NotificationHelper {

    const val CHANNEL_ID = "meetmate_alarms_v4"
    const val CHANNEL_NAME = "Meeting Alarms"
    private val OLD_CHANNEL_IDS = listOf("meetmate_reminders", "meetmate_alarms_v2", "meetmate_alarms_v3")

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
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
            setSound(null, null)
            setBypassDnd(true)
        }

        manager.createNotificationChannel(channel)
    }

    fun canScheduleAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            return alarmManager.canScheduleExactAlarms()
        }
        return true
    }

    fun getExactAlarmSettingsIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        } else {
            Intent()
        }
    }

    fun scheduleReminder(context: Context, meeting: Meeting) {
        Log.d("MeetMateAlarm", "scheduleReminder called for: ${meeting.title}, time=${meeting.dateTimeMillis}, reminder=${meeting.reminderMinutesBefore}min")
        if (meeting.dateTimeMillis <= System.currentTimeMillis()) {
            Log.d("MeetMateAlarm", "Meeting time is in the past, skipping")
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("meeting_id", meeting.id)
            putExtra("meeting_title", meeting.title)
            putExtra("meeting_person", meeting.personName)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            meeting.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        var triggerTime = meeting.dateTimeMillis - (meeting.reminderMinutesBefore * 60 * 1000L)

        if (triggerTime <= System.currentTimeMillis()) {
            Log.d("MeetMateAlarm", "Trigger time is past, firing in 5 seconds")
            triggerTime = System.currentTimeMillis() + 5_000L
        }
        Log.d("MeetMateAlarm", "Scheduling alarm at triggerTime=$triggerTime (in ${(triggerTime - System.currentTimeMillis()) / 1000}s)")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerTime, pendingIntent),
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerTime, pendingIntent),
                pendingIntent
            )
        }
    }

    fun cancelReminder(context: Context, meetingId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            meetingId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
