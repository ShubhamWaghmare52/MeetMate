package com.meetmate.app.util

import com.meetmate.app.data.Meeting
import com.meetmate.app.data.Repeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class RecurrenceTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0) =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    // Thursday 30 Oct 2025, 10:00
    private val anchor = at(2025, 10, 30, 10)

    private fun next(repeat: Repeat, from: Long) = Recurrence.nextOccurrence(anchor, repeat, from, zone)

    @Test
    fun `non repeating returns its own time even when past`() {
        assertEquals(anchor, next(Repeat.NONE, at(2025, 11, 5, 9)))
    }

    @Test
    fun `future anchor is returned as is`() {
        assertEquals(anchor, next(Repeat.DAILY, at(2025, 10, 29, 9)))
    }

    @Test
    fun daily() {
        assertEquals(at(2025, 11, 2, 10), next(Repeat.DAILY, at(2025, 11, 2, 9)))
        assertEquals(at(2025, 11, 3, 10), next(Repeat.DAILY, at(2025, 11, 2, 10, 1)))
    }

    @Test
    fun weekly() {
        assertEquals(at(2025, 11, 6, 10), next(Repeat.WEEKLY, at(2025, 10, 31, 8)))
        assertEquals(at(2025, 11, 13, 10), next(Repeat.WEEKLY, at(2025, 11, 6, 11)))
    }

    @Test
    fun `weekdays skip the weekend`() {
        // Friday after 10:00 → Monday
        assertEquals(at(2025, 11, 3, 10), next(Repeat.WEEKDAYS, at(2025, 10, 31, 12)))
        assertEquals(at(2025, 11, 3, 10), next(Repeat.WEEKDAYS, at(2025, 11, 1, 8)))
    }

    @Test
    fun `wall clock time survives a DST change`() {
        val berlin = ZoneId.of("Europe/Berlin")
        val start = LocalDateTime.of(2025, 10, 20, 9, 0).atZone(berlin).toInstant().toEpochMilli()
        val from = LocalDateTime.of(2025, 10, 27, 8, 0).atZone(berlin).toInstant().toEpochMilli()
        val expected = LocalDateTime.of(2025, 10, 27, 9, 0).atZone(berlin).toInstant().toEpochMilli()
        assertEquals(expected, Recurrence.nextOccurrence(start, Repeat.WEEKLY, from, berlin))
    }
}

class ReminderScheduleTest {

    private val minute = 60_000L
    private val now = 1_000_000_000_000L

    private fun meeting(
        at: Long,
        reminder: Int = 30,
        repeat: Repeat = Repeat.NONE,
        reminded: Long = 0,
        completed: Boolean = false
    ) = Meeting(
        id = 1, title = "t", personName = "", dateTimeMillis = at,
        reminderMinutesBefore = reminder, repeat = repeat,
        lastRemindedOccurrence = reminded, isCompleted = completed
    )

    @Test
    fun `rings reminder minutes before`() {
        val plan = ReminderSchedule.plan(meeting(now + 120 * minute), now)!!
        assertEquals(now + 90 * minute, plan.triggerMillis)
    }

    @Test
    fun `late reminder rings almost immediately`() {
        val plan = ReminderSchedule.plan(meeting(now + 10 * minute), now)!!
        assertEquals(now + ReminderSchedule.LATE_DELAY_MILLIS, plan.triggerMillis)
    }

    @Test
    fun `past or completed meetings have nothing to schedule`() {
        assertNull(ReminderSchedule.plan(meeting(now - minute), now))
        assertNull(ReminderSchedule.plan(meeting(now + 120 * minute, completed = true), now))
    }

    @Test
    fun `an occurrence that already rang is not rung again`() {
        val at = now + 10 * minute
        assertNull(ReminderSchedule.plan(meeting(at, reminded = at), now))
    }

    @Test
    fun `repeating meeting moves on to the next occurrence after ringing`() {
        val at = now + 10 * minute
        val plan = ReminderSchedule.plan(meeting(at, repeat = Repeat.DAILY, reminded = at), now)!!
        assertEquals(at + 24 * 60 * minute, plan.occurrenceMillis)
        assertEquals(at + 24 * 60 * minute - 30 * minute, plan.triggerMillis)
    }
}
