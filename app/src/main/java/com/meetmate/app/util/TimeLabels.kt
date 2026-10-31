package com.meetmate.app.util

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Plain-Kotlin formatting helpers for the UI, kept Android-free so they can be unit tested. */
object TimeLabels {

    private val dayFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())
    private val headerDateFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())

    /** "in 5 min", "in 2h 15m", "in 3 days", "now", "12 min ago". */
    fun countdown(targetMillis: Long, nowMillis: Long): String {
        val diffMinutes = (targetMillis - nowMillis) / 60_000
        val future = diffMinutes >= 0
        val minutes = kotlin.math.abs(diffMinutes)
        val text = when {
            minutes < 1 -> return "now"
            minutes < 60 -> "$minutes min"
            minutes < 24 * 60 -> {
                val h = minutes / 60
                val m = minutes % 60
                if (m == 0L) "${h}h" else "${h}h ${m}m"
            }
            else -> {
                val d = minutes / (24 * 60)
                if (d == 1L) "1 day" else "$d days"
            }
        }
        return if (future) "in $text" else "$text ago"
    }

    /** Section header for a day: "Today", "Tomorrow", "Yesterday" or "Mon, 3 Nov". */
    fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(dayFormatter)
    }

    fun headerDate(date: LocalDate): String = date.format(headerDateFormatter)

    fun greeting(time: LocalTime): String = when (time.hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Hello, night owl"
    }

    fun reminderLabel(minutes: Int): String = when {
        minutes < 60 -> "$minutes min before"
        minutes % 60 == 0 -> if (minutes == 60) "1 hour before" else "${minutes / 60} hours before"
        else -> "$minutes min before"
    }

    fun reminderShort(minutes: Int): String = when {
        minutes < 60 -> "${minutes}m"
        minutes % 60 == 0 -> "${minutes / 60}h"
        else -> "${minutes}m"
    }

    /** Up to two initials from a name: "rahul sharma" → "RS". */
    fun initials(name: String): String =
        name.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
}
