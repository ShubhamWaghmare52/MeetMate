package com.meetmate.app.parser

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale

data class ParsedMeeting(
    val personName: String? = null,
    val time: LocalTime? = null,
    val date: LocalDate? = null,
    val title: String? = null,
    val needsTimePicker: Boolean = false,
    val needsDatePicker: Boolean = false
)

object NaturalLanguageParser {

    /**
     * [today] and [now] default to the clock; tests pass fixed values so results are deterministic.
     */
    fun parse(
        input: String,
        today: LocalDate = LocalDate.now(),
        now: LocalTime = LocalTime.now()
    ): ParsedMeeting {
        val lower = input.lowercase(Locale.ROOT).trim()

        val person = extractPerson(lower)
        val time = extractTime(lower)
        val date = extractDate(lower, today)
        val title = buildTitle(lower, person)

        return ParsedMeeting(
            personName = person?.split(" ")?.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } },
            time = time,
            date = date ?: if (time != null) {
                if (time.isBefore(now)) today.plusDays(1) else today
            } else null,
            title = title,
            needsTimePicker = time == null,
            needsDatePicker = date == null && !lower.contains("today") && !lower.contains("tomorrow")
        )
    }

    private fun extractPerson(input: String): String? {
        val stopWords = setOf(
            "me", "the", "a", "an", "my", "at", "on", "in", "for", "to", "about",
            "today", "tomorrow", "monday", "tuesday", "wednesday", "thursday",
            "friday", "saturday", "sunday", "mon", "tue", "wed", "thu", "fri", "sat", "sun"
        )
        val withPattern = Regex("with\\s+([a-zA-Z]+)(?:\\s+([a-zA-Z]+))?")
        withPattern.find(input)?.let { match ->
            val firstName = match.groupValues[1].trim()
            if (firstName in stopWords) return null
            val secondWord = match.groupValues[2].trim()
            return if (secondWord.isNotEmpty() && secondWord !in stopWords) {
                "$firstName $secondWord"
            } else {
                firstName
            }
        }
        return null
    }

    private fun extractTime(input: String): LocalTime? {
        // "at 20" or "at 8" or "as 6:15" (voice recognition sometimes says "as")
        Regex("(?:at|as)\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?", RegexOption.IGNORE_CASE).find(input)?.let { match ->
            var hour = match.groupValues[1].toInt()
            val minute = if (match.groupValues[2].isNotEmpty()) match.groupValues[2].toInt() else 0
            val ampm = match.groupValues[3].lowercase()

            when {
                ampm == "pm" && hour < 12 -> hour += 12
                ampm == "am" && hour == 12 -> hour = 0
                ampm.isEmpty() && hour in 1..7 -> hour += 12
            }

            if (hour in 0..23 && minute in 0..59) {
                return LocalTime.of(hour, minute)
            }
        }

        // "8pm", "3:30pm"
        Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)", RegexOption.IGNORE_CASE).find(input)?.let { match ->
            var hour = match.groupValues[1].toInt()
            val minute = if (match.groupValues[2].isNotEmpty()) match.groupValues[2].toInt() else 0
            val ampm = match.groupValues[3].lowercase()

            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0

            if (hour in 0..23 && minute in 0..59) {
                return LocalTime.of(hour, minute)
            }
        }

        return null
    }

    private fun extractDate(input: String, today: LocalDate): LocalDate? {
        // Longest phrase first: "day after tomorrow" also contains "tomorrow".
        if (input.contains("day after tomorrow")) return today.plusDays(2)
        if (input.contains("today") || input.contains("tonight")) return today
        if (input.contains("tomorrow")) return today.plusDays(1)

        val dayMap = mapOf(
            "monday" to DayOfWeek.MONDAY,
            "tuesday" to DayOfWeek.TUESDAY,
            "wednesday" to DayOfWeek.WEDNESDAY,
            "thursday" to DayOfWeek.THURSDAY,
            "friday" to DayOfWeek.FRIDAY,
            "saturday" to DayOfWeek.SATURDAY,
            "sunday" to DayOfWeek.SUNDAY,
            "mon" to DayOfWeek.MONDAY,
            "tue" to DayOfWeek.TUESDAY,
            "wed" to DayOfWeek.WEDNESDAY,
            "thu" to DayOfWeek.THURSDAY,
            "fri" to DayOfWeek.FRIDAY,
            "sat" to DayOfWeek.SATURDAY,
            "sun" to DayOfWeek.SUNDAY
        )

        for ((name, dayOfWeek) in dayMap) {
            if (Regex("\\b$name\\b").containsMatchIn(input)) {
                val next = today.with(TemporalAdjusters.nextOrSame(dayOfWeek))
                return if (next == today) today else next
            }
        }

        // "on 15th", "on 15"
        Regex("on\\s+(\\d{1,2})(?:st|nd|rd|th)?").find(input)?.let { match ->
            val day = match.groupValues[1].toInt()
            if (day in 1..31) {
                val candidate = today.withDayOfMonth(day.coerceAtMost(today.lengthOfMonth()))
                return if (candidate.isBefore(today)) candidate.plusMonths(1) else candidate
            }
        }

        return null
    }

    private fun buildTitle(input: String, person: String?): String {
        val cleaned = input
            .replace(Regex("(?:at|as)\\s+\\d{1,2}(:\\d{2})?\\s*(am|pm)?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\d{1,2}(:\\d{2})?\\s*(am|pm)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("with\\s+[a-zA-Z]+(?:\\s+[a-zA-Z]+)?"), "")
            .replace(Regex("on\\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tue|wed|thu|fri|sat|sun|today|tomorrow|\\d{1,2}(st|nd|rd|th)?)", RegexOption.IGNORE_CASE), "")
            // Date words that weren't preceded by "on", e.g. "lunch tomorrow at 1pm".
            .replace(Regex("\\b(?:(?:this|next)\\s+)?(day after tomorrow|today|tonight|tomorrow|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            // Connectors left dangling once the date/time is gone: "call at" → "call".
            .replace(Regex("(?:\\s+(?:at|on|with|for))+$"), "")
            .trim()

        return if (cleaned.isNotBlank() && cleaned.length > 2) {
            cleaned.replaceFirstChar { it.uppercase() }
        } else {
            "Meeting${person?.let { " with ${it.replaceFirstChar { c -> c.uppercase() }}" } ?: ""}"
        }
    }

    fun toEpochMillis(date: LocalDate, time: LocalTime): Long {
        return LocalDateTime.of(date, time)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }
}
