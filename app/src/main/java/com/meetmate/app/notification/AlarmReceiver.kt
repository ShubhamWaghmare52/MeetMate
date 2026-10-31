package com.meetmate.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.meetmate.app.data.MeetingDatabase
import com.meetmate.app.widget.NextMeetingWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Fires at reminder time (or when a snooze ends) and hands the ringing over to [AlarmService]. */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val meetingId = intent.getLongExtra(NotificationHelper.EXTRA_MEETING_ID, -1)
        val occurrence = intent.getLongExtra(NotificationHelper.EXTRA_OCCURRENCE, 0)
        Log.d("MeetMateAlarm", "AlarmReceiver ${intent.action} meeting=$meetingId occurrence=$occurrence")
        if (meetingId < 0) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = MeetingDatabase.getDatabase(context).meetingDao()
                val meeting = dao.getMeetingById(meetingId)
                if (meeting == null || meeting.isCompleted) return@launch

                if (intent.action == NotificationHelper.ACTION_REMIND) {
                    // Remember this occurrence rang, then queue the next one for repeating meetings.
                    dao.markReminded(meetingId, occurrence)
                    NotificationHelper.scheduleReminder(
                        context,
                        meeting.copy(lastRemindedOccurrence = occurrence)
                    )
                }

                NextMeetingWidget.updateAll(context)
                AlarmService.start(
                    context,
                    meeting,
                    occurrence,
                    snoozed = intent.action == NotificationHelper.ACTION_SNOOZE_FIRE
                )
            } finally {
                pending.finish()
            }
        }
    }
}
