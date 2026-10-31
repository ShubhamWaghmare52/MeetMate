package com.meetmate.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat

class DismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("MeetMateAlarm", "Dismiss action received")
        AlarmSoundManager.stop()
        val meetingId = intent.getIntExtra("meeting_id", -1)
        if (meetingId != -1) {
            NotificationManagerCompat.from(context).cancel(meetingId)
        }
    }
}
