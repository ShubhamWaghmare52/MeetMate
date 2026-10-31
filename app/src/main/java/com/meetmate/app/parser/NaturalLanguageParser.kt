package com.meetmate.app.parser

import com.meetmate.app.data.Repeat
import com.meetmate.app.util.Links
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.MonthDay
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale

data class ParsedMeeting(
    val personName: String? = null,
    val time: LocalTime? = null,
    val date: LocalDate? = null,
    val title: String? = null,
    val repeat: Repeat? = null,
    val meetingLink: String? = null,
    val needsTimePicker: Boolean = false,
    val needsDatePicker: Boolean = false
)

/**
 * Turns a sentence like "Standup with Priya every weekday at 10am" into meeting fields.
 * Plain Kotlin (no Android types) so it is covered by JVM unit tests.
 */
object NaturalLanguageParser {

    private val weekdays = linkedMapOf(
        "monday" to DayOfWeek.MONDAY, "tuesday" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY, "thursday" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY, "saturday" to DayOfWeek.SATURDAY, "sunday" to DayOfWeek.SUNDAY,
        "mon" to DayOfWeek.MONDAY, "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY,
        "wed" to DayOfWeek.WEDNESDAY, "thu" to DayOfWeek.THURSDAY, "thur" to DayOfWeek.THURSDAY,
        "thurs" to DayOfWeek.THURSDAY, "fri" to DayOfWeek.FRIDAY, "sat" to DayOfWeek.SATURDAY,
        "sun" to DayOfWeek.SUNDAY
    )
    private val weekdayAlternation = weekdays.keys.sortedByDescending { it.length }.joinToString("|")

    private val months = listOf(
        "january", "february", "march", "april", "may", "june",
        "july", "august", "september", "october", "november", "december"
    )
    // "sept" before "sep" and full names before 3-letter forms so the longest form wins.
    private val monthAlternation =
        (months + months.map { it.take(3) } + "sept").distinct().sortedByDescending { it.length }.joinToString("|")

    private const val ORDINAL = "(?:st|nd|rd|th)?"
    private val dayMonthRegex = Regex("""\b(\d{1,2})$ORDINAL\s+(?:of\s+)?($monthAlternation)\b""")
    private val monthDayRegex = Regex("""\b($monthAlternation)\s+(\d{1,2})$ORDINAL\b""")
    private val relativeRegex =
        Regex("""\bin\s+(an?|half an|\d{1,3})\s*(minutes?|mins?|hours?|hrs?|h|m)\b""")
    private val clockAtRegex = Regex("""\b(?:at|as)\s+(\d{1,2})(?::(\d{2}))?\s*(am|pm)?""")
    private val clockAmPmRegex = Regex("""\b(\d{1,2})(?::(\d{2}))?\s*(am|pm)\b""")
    private val namedTimeRegex = Regex("""\b(?:at\s+)?(noon|midday|midnight)\b""")
    private val dayOfMonthRegex = Regex("""\bon\s+(?:the\s+)?(\d{1,2})$ORDINAL\b""")

    private val personStopWords = setOf(
        "me", "the", "a", "an", "my", "at", "on", "in", "for", "to", "about", "every", "next", "this",
        "today", "tomorrow", "tonight", "noon", "midnight", "daily", "weekly"
    ) + weekdays.keys + months

    /**
     * [today] and [now] default to the clock; tests pass fixed values so results are deterministic.
     */
    fun parse(
        input: String,
        today: LocalDate = LocalDate.now(),
        now: LocalTime = LocalTime.now()
    ): ParsedMeeting {
        val link = Links.find(input)
        val withoutLink = if (link != null) input.replace(link, " ") else input
        val lower = withoutLink.lowercase(Locale.ROOT).trim()

        val person = extractPerson(lower)
        val repeat = extractRepeat(lower)
        val relative = extractRelative(lower, today, now)
        val time = relative?.toLocalTime() ?: extractTime(lower)
        val date = relative?.toLocalDate() ?: extractDate(lower, today)
        val title = buildTitle(lower, person)

        return ParsedMeeting(
            personName = person?.split(" ")?.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } },
            time = time,
            date = date ?: if (time != null) {
                if (time.isBefore(now)) today.plusDays(1) else today
            } else null,
            title = title,
            repeat = repeat,
            meetingLink = link,
            needsTimePicker = time == null,
            needsDatePicker = date == null && time == null
        )
    }

    private fun extractPerson(input: String): String? {
        val match = Regex("""\bwith\s+([a-z]+)(?:\s+([a-z]+))?""").find(input) ?: return null
        val firstName = match.groupValues[1]
        if (firstName in personStopWords) return null
        val secondWord = match.groupValues[2]
        return if (secondWord.isNotEmpty() && secondWord !in personStopWords) "$firstName $secondWord" else firstName
    }

    private fun extractRepeat(input: String): Repeat? = when {
        Regex("""\b(every\s+weekday|weekdays|every\s+working\s+day)\b""").containsMatchIn(input) -> Repeat.WEEKDAYS
        Regex("""\b(every\s*day|daily)\b""").containsMatchIn(input) -> Repeat.DAILY
        Regex("""\b(every\s+week|weekly|every\s+(?:$weekdayAlternation))\b""").containsMatchIn(input) -> Repeat.WEEKLY
        else -> null
    }

    /** "in 30 minutes", "in an hour", "in half an hour", "in 2 hrs". */
    private fun extractRelative(input: String, today: LocalDate, now: LocalTime): LocalDateTime? {
        val match = relativeRegex.find(input) ?: return null
        val amount = match.groupValues[1]
        val isHours = match.groupValues[2].startsWith("h")
        val minutes = when {
            amount == "half an" -> 30L
            amount == "a" || amount == "an" -> if (isHours) 60L else 1L
            isHours -> amount.toLong() * 60
            else -> amount.toLong()
        }
        return LocalDateTime.of(today, now.withSecond(0).withNano(0)).plusMinutes(minutes)
    }

    private fun extractTime(input: String): LocalTime? {
        namedTimeRegex.find(input)?.let {
            return if (it.groupValues[1] == "midnight") LocalTime.MIDNIGHT else LocalTime.NOON
        }

        // "at 8", "at 6:15", "as 6:15" (speech recognition often hears "at" as "as")
        clockAtRegex.find(input)?.let { match ->
            var hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            when (match.groupValues[3]) {
                "pm" -> if (hour < 12) hour += 12
                "am" -> if (hour == 12) hour = 0
                // No am/pm: 1–7 is almost always afternoon for a meeting.
                else -> if (hour in 1..7) hour += 12
            }
            if (hour in 0..23 && minute in 0..59) return LocalTime.of(hour, minute)
        }

        // "8pm", "3:30 am"
        clockAmPmRegex.find(input)?.let { match ->
            var hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            if (match.groupValues[3] == "pm" && hour < 12) hour += 12
            if (match.groupValues[3] == "am" && hour == 12) hour = 0
            if (hour in 0..23 && minute in 0..59) return LocalTime.of(hour, minute)
        }
        return null
    }

    private fun extractDate(input: String, today: LocalDate): LocalDate? {
        // Longest phrase first: "day after tomorrow" also contains "tomorrow".
        if (input.contains("day after tomorrow")) return today.plusDays(2)
        if (Regex("""\b(today|tonight)\b""").containsMatchIn(input)) return today
        if (Regex("""\btomorrow\b""").containsMatchIn(input)) return today.plusDays(1)

        // "5 nov", "5th of november", "november 5th"
        val dayMonth = dayMonthRegex.find(input)?.let { it.groupValues[1].toInt() to it.groupValues[2] }
            ?: monthDayRegex.find(input)?.let { it.groupValues[2].toInt() to it.groupValues[1] }
        if (dayMonth != null) {
            val month = months.indexOfFirst { it.startsWith(dayMonth.second.take(3)) } + 1
            if (month > 0 && dayMonth.first in 1..31) {
                val monthDay = runCatching { MonthDay.of(month, dayMonth.first) }.getOrNull()
                if (monthDay != null) {
                    // Feb 29 resolves to the next year that has one, via MonthDay.atYear's clamping.
                    val thisYear = monthDay.atYear(today.year)
                    return if (thisYear.isBefore(today)) monthDay.atYear(today.year + 1) else thisYear
                }
            }
        }

        // "next monday" is strictly after today; "monday" / "this monday" / "every monday" may be today.
        Regex("""\b(next\s+|this\s+|every\s+)?($weekdayAlternation)\b""").find(input)?.let { match ->
            val day = weekdays.getValue(match.groupValues[2])
            val adjuster = if (match.groupValues[1].startsWith("next")) {
                TemporalAdjusters.next(day)
            } else {
                TemporalAdjusters.nextOrSame(day)
            }
            return today.with(adjuster)
        }

        if (Regex("""\bnext\s+week\b""").containsMatchIn(input)) return today.plusWeeks(1)

        // "on 15th", "on the 3rd"
        dayOfMonthRegex.find(input)?.let { match ->
            val day = match.groupValues[1].toInt()
            if (day in 1..31) {
                val candidate = today.withDayOfMonth(day.coerceAtMost(today.lengthOfMonth()))
                return if (candidate.isBefore(today)) {
                    val next = today.plusMonths(1)
                    next.withDayOfMonth(day.coerceAtMost(next.lengthOfMonth()))
                } else candidate
            }
        }

        return null
    }

    private fun buildTitle(input: String, person: String?): String {
        val cleaned = input
            .replace(relativeRegex, "")
            .replace(namedTimeRegex, "")
            .replace(Regex("""\b(?:at|as)\s+\d{1,2}(:\d{2})?\s*(am|pm)?"""), "")
            .replace(clockAmPmRegex, "")
            .replace(Regex("""\bwith\s+[a-z]+(?:\s+[a-z]+)?"""), "")
            .replace(Regex("""\b(?:on\s+)?(?:the\s+)?\d{1,2}$ORDINAL\s+(?:of\s+)?(?:$monthAlternation)\b"""), "")
            .replace(Regex("""\b(?:on\s+)?(?:$monthAlternation)\s+\d{1,2}$ORDINAL\b"""), "")
            .replace(Regex("""\b(every\s+weekday|weekdays|every\s+working\s+day|every\s*day|daily|every\s+week|weekly)\b"""), "")
            .replace(Regex("""\b(?:on\s+|every\s+|next\s+|this\s+)?(day after tomorrow|today|tonight|tomorrow|$weekdayAlternation)\b"""), "")
            .replace(Regex("""\bnext\s+week\b"""), "")
            .replace(dayOfMonthRegex, "")
            .replace(Regex("""\s+"""), " ")
            .trim()
            // Connectors left dangling once the date/time is gone: "call at" → "call".
            .replace(Regex("""(?:\s+(?:at|on|with|for|in|every))+$"""), "")
            .replace(Regex("""^(?:(?:at|on|with|for|in)\s+)+"""), "")
            .trim()

        return if (cleaned.length > 2) {
            cleaned.replaceFirstChar { it.uppercase() }
        } else {
            "Meeting${person?.let { " with ${it.split(" ").joinToString(" ") { w -> w.replaceFirstChar { c -> c.uppercase() } }}" } ?: ""}"
        }
    }

    fun toEpochMillis(date: LocalDate, time: LocalTime): Long {
        return LocalDateTime.of(date, time)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }
}
