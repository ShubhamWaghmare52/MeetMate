package com.meetmate.app.notification

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.meetmate.app.MainActivity
import com.meetmate.app.R

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("MeetMateAlarm", "AlarmReceiver.onReceive triggered!")
        val meetingId = intent.getLongExtra("meeting_id", -1)
        val title = intent.getStringExtra("meeting_title") ?: "Meeting"
        val person = intent.getStringExtra("meeting_person") ?: ""
        Log.d("MeetMateAlarm", "Meeting: id=$meetingId, title=$title, person=$person")

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("meeting_id", meetingId)
        }
        val tapPendingIntent = PendingIntent.getActivity(
            context, meetingId.toInt(), tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, DismissReceiver::class.java).apply {
            putExtra("meeting_id", meetingId.toInt())
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context, (meetingId.toInt() + 10000), dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Upcoming: $title")
            .setContentText(if (person.isNotBlank()) "Meeting with $person" else "Your meeting is coming up!")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSilent(true)
            .setOngoing(true)
            .setContentIntent(tapPendingIntent)
            .setDeleteIntent(dismissPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(tapPendingIntent, true)
            .addAction(R.drawable.ic_notification, "Dismiss", dismissPendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(meetingId.toInt(), notification)
            Log.d("MeetMateAlarm", "Notification posted successfully")
        } catch (e: SecurityException) {
            Log.e("MeetMateAlarm", "SecurityException posting notification", e)
        }

        AlarmSoundManager.play(context)
    }
}
