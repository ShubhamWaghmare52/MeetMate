package com.meetmate.app.util

import com.meetmate.app.data.Meeting
import com.meetmate.app.data.Repeat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Occurrence maths for repeating meetings. Plain Kotlin so it can be unit tested. */
object Recurrence {

    /**
     * First occurrence at or after [fromMillis]. Non-repeating meetings just return their own time
     * (which may be in the past). Uses local date-time maths so the wall-clock time survives DST.
     */
    fun nextOccurrence(
        anchorMillis: Long,
        repeat: Repeat,
        fromMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long {
        if (repeat == Repeat.NONE || anchorMillis >= fromMillis) return anchorMillis

        val anchor = LocalDateTime.ofInstant(Instant.ofEpochMilli(anchorMillis), zone)
        val from = LocalDateTime.ofInstant(Instant.ofEpochMilli(fromMillis), zone)
        var candidate = from.toLocalDate().atTime(anchor.toLocalTime())

        fun LocalDateTime.millis() = atZone(zone).toInstant().toEpochMilli()

        when (repeat) {
            Repeat.DAILY -> if (candidate.millis() < fromMillis) candidate = candidate.plusDays(1)
            Repeat.WEEKLY -> {
                candidate = candidate.with(TemporalAdjusters.nextOrSame(anchor.dayOfWeek))
                if (candidate.millis() < fromMillis) candidate = candidate.plusWeeks(1)
            }
            Repeat.WEEKDAYS -> {
                while (candidate.millis() < fromMillis || candidate.dayOfWeek.isWeekend()) {
                    candidate = candidate.plusDays(1)
                }
            }
            Repeat.NONE -> Unit
        }
        return candidate.millis()
    }

    private fun DayOfWeek.isWeekend() = this == DayOfWeek.SATURDAY || this == DayOfWeek.SUNDAY
}

/** The occurrence to show for this meeting right now: the next one for repeating meetings. */
fun Meeting.occurrenceMillis(nowMillis: Long): Long =
    Recurrence.nextOccurrence(dateTimeMillis, repeat, nowMillis)

/** When the reminder for [occurrenceMillis] should ring, and for which occurrence. */
data class ReminderPlan(val occurrenceMillis: Long, val triggerMillis: Long)

object ReminderSchedule {

    /** Grace delay when a reminder's time has already passed but the meeting hasn't started. */
    const val LATE_DELAY_MILLIS = 5_000L

    /**
     * Picks the next reminder to schedule, or null if there is nothing left to remind about.
     * An occurrence whose reminder already rang ([Meeting.lastRemindedOccurrence]) is skipped, so
     * re-scheduling on app start, boot or update never rings the same meeting twice.
     */
    fun plan(meeting: Meeting, nowMillis: Long): ReminderPlan? {
        if (meeting.isCompleted) return null

        var occurrence = meeting.occurrenceMillis(nowMillis)
        if (occurrence == meeting.lastRemindedOccurrence) {
            if (meeting.repeat == Repeat.NONE) return null
            occurrence = Recurrence.nextOccurrence(meeting.dateTimeMillis, meeting.repeat, occurrence + 1)
        }
        if (occurrence <= nowMillis) return null

        val trigger = occurrence - meeting.reminderMinutesBefore * 60_000L
        return ReminderPlan(
            occurrenceMillis = occurrence,
            triggerMillis = if (trigger <= nowMillis) nowMillis + LATE_DELAY_MILLIS else trigger
        )
    }
}
