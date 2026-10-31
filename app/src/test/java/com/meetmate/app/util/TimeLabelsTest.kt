package com.meetmate.app.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class TimeLabelsTest {

    private val minute = 60_000L

    @Test
    fun countdown() {
        assertEquals("now", TimeLabels.countdown(30_000, 0))
        assertEquals("in 5 min", TimeLabels.countdown(5 * minute, 0))
        assertEquals("in 2h", TimeLabels.countdown(120 * minute, 0))
        assertEquals("in 2h 15m", TimeLabels.countdown(135 * minute, 0))
        assertEquals("in 1 day", TimeLabels.countdown(25 * 60 * minute, 0))
        assertEquals("in 3 days", TimeLabels.countdown(3 * 24 * 60 * minute, 0))
        assertEquals("10 min ago", TimeLabels.countdown(0, 10 * minute))
    }

    @Test
    fun dayLabel() {
        val today = LocalDate.of(2025, 10, 30)
        assertEquals("Today", TimeLabels.dayLabel(today, today))
        assertEquals("Tomorrow", TimeLabels.dayLabel(today.plusDays(1), today))
        assertEquals("Yesterday", TimeLabels.dayLabel(today.minusDays(1), today))
    }

    @Test
    fun greeting() {
        assertEquals("Good morning", TimeLabels.greeting(LocalTime.of(8, 0)))
        assertEquals("Good afternoon", TimeLabels.greeting(LocalTime.of(13, 0)))
        assertEquals("Good evening", TimeLabels.greeting(LocalTime.of(19, 0)))
        assertEquals("Hello, night owl", TimeLabels.greeting(LocalTime.of(2, 0)))
    }

    @Test
    fun reminderLabels() {
        assertEquals("15 min before", TimeLabels.reminderLabel(15))
        assertEquals("1 hour before", TimeLabels.reminderLabel(60))
        assertEquals("30m", TimeLabels.reminderShort(30))
        assertEquals("1h", TimeLabels.reminderShort(60))
    }

    @Test
    fun initials() {
        assertEquals("RS", TimeLabels.initials("rahul sharma"))
        assertEquals("P", TimeLabels.initials(" Priya "))
        assertEquals("AB", TimeLabels.initials("Ann Bee Cee"))
        assertEquals("", TimeLabels.initials(""))
    }
}
