package com.meetmate.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.meetmate.app.MainActivity
import com.meetmate.app.R
import com.meetmate.app.data.MeetingDatabase
import com.meetmate.app.util.TimeLabels
import com.meetmate.app.util.occurrenceMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Home-screen widget: the next meeting with a countdown, and a mic button to add one by voice. */
class NextMeetingWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                updateAll(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** Redraws every placed widget. Call whenever meetings change. */
        suspend fun updateAll(context: Context) = withContext(Dispatchers.IO) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NextMeetingWidget::class.java))
            if (ids.isEmpty()) return@withContext

            val now = System.currentTimeMillis()
            val next = MeetingDatabase.getDatabase(context).meetingDao().getActiveMeetings()
                .map { it to it.occurrenceMillis(now) }
                .filter { it.second >= now }
                .minByOrNull { it.second }

            val views = RemoteViews(context.packageName, R.layout.widget_next_meeting)
            if (next == null) {
                views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_empty_title))
                views.setTextViewText(R.id.widget_subtitle, context.getString(R.string.widget_empty_body))
            } else {
                val (meeting, at) = next
                val dateTime = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
                val day = TimeLabels.dayLabel(dateTime.toLocalDate(), LocalDate.now())
                val time = dateTime.format(DateTimeFormatter.ofPattern("h:mm a"))
                views.setTextViewText(R.id.widget_title, meeting.title)
                views.setTextViewText(
                    R.id.widget_subtitle,
                    "$day · $time · ${TimeLabels.countdown(at, now)}"
                )
            }

            views.setOnClickPendingIntent(
                R.id.widget_content,
                activityIntent(context, Intent.ACTION_MAIN, requestCode = 0)
            )
            views.setOnClickPendingIntent(
                R.id.widget_add,
                activityIntent(context, MainActivity.ACTION_NEW_MEETING_VOICE, requestCode = 1)
            )
            manager.updateAppWidget(ids, views)
        }

        private fun activityIntent(context: Context, action: String, requestCode: Int): PendingIntent =
            PendingIntent.getActivity(
                context,
                requestCode,
                Intent(context, MainActivity::class.java)
                    .setAction(action)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }
}
