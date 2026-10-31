package com.meetmate.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.meetmate.app.data.MeetingDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        CoroutineScope(Dispatchers.IO).launch {
            val dao = MeetingDatabase.getDatabase(context).meetingDao()
            val meetings = dao.getUpcomingMeetings().first()
            meetings.forEach { meeting ->
                NotificationHelper.scheduleReminder(context, meeting)
            }
        }
    }
}
