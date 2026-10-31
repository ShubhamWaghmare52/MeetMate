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
    // Minutes may follow a colon or a dot: "6:15", "12.30".
    private const val MINUTES = """(?:[:.](\d{2}))?"""
    private val clockAtRegex = Regex("""\b(?:at|as)\s+(\d{1,2})$MINUTES\s*(am|pm)?""")
    private val clockAmPmRegex = Regex("""\b(\d{1,2})$MINUTES\s*(am|pm)\b""")
    // "from 3 to 4pm", "between 11 and 1pm", "2-3pm". Only the start is used. A bare "1 to 1" (a one-to-one)
    // is not a range, so a range needs "from"/"between" in front or am/pm at its end.
    private val rangeRegex = Regex(
        """\b(from\s+|between\s+)?(\d{1,2})$MINUTES\s*(am|pm)?\s*(?:-|–|to|till|until|and)\s*(\d{1,2})$MINUTES\s*(am|pm)?\b"""
    )
    // "31/12", "5/11/2026": day first, as written in India and the UK; flipped when only that reading is a date.
    private val numericDateRegex = Regex("""\b(\d{1,2})/(\d{1,2})(?:/(\d{2}|\d{4}))?\b""")
    // Words that say which half of the day a bare hour means: "dinner at 8" is 8 pm, "gym at 6 in the morning" 6 am.
    private val eveningRegex = Regex("""\b(evening|night|tonight|dinner|afternoon)\b""")
    private val morningRegex = Regex("""\b(morning|breakfast)\b""")
    private val dayPartPhraseRegex = Regex("""\b(?:in\s+the|this|at)\s+(?:morning|afternoon|evening|night)\b""")
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
        val withoutLink = if (link != null) input.replace(link.raw, " ") else input
        val lower = withoutLink.lowercase(Locale.ROOT).trim()

        val person = extractPerson(lower)
        val repeat = extractRepeat(lower)
        val relative = extractRelative(lower, today, now)
        val time = relative?.toLocalTime() ?: extractTime(lower)
        val date = relative?.toLocalDate() ?: extractDate(lower, today)
        val title = buildTitle(lower, person)
        val resolvedDate = date ?: if (time != null) {
            if (time.isBefore(now)) today.plusDays(1) else today
        } else null

        return ParsedMeeting(
            personName = person?.split(" ")?.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } },
            time = time,
            // "every weekday" said on a Saturday starts on Monday, not on the weekend.
            date = if (repeat == Repeat.WEEKDAYS) resolvedDate?.let(::nextWeekdayOrSame) else resolvedDate,
            title = title,
            repeat = repeat,
            meetingLink = link?.url,
            needsTimePicker = time == null,
            needsDatePicker = date == null && time == null
        )
    }

    private fun nextWeekdayOrSame(date: LocalDate): LocalDate = when (date.dayOfWeek) {
        DayOfWeek.SATURDAY -> date.plusDays(2)
        DayOfWeek.SUNDAY -> date.plusDays(1)
        else -> date
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

    /** An hour with no am/pm, read with the words around it. */
    private fun bareHour(hour: Int, input: String): Int = when {
        hour !in 1..11 -> hour
        morningRegex.containsMatchIn(input) -> hour
        eveningRegex.containsMatchIn(input) -> hour + 12
        // Otherwise 1–7 is almost always afternoon for a meeting.
        hour <= 7 -> hour + 12
        else -> hour
    }

    private fun withMeridiem(hour: Int, meridiem: String): Int = when (meridiem) {
        "pm" -> if (hour < 12) hour + 12 else hour
        "am" -> if (hour == 12) 0 else hour
        else -> hour
    }

    private fun extractTime(input: String): LocalTime? {
        rangeRegex.find(input)?.let { match ->
            val (prefix, startH, startM, startAmPm, endH, _, endAmPm) = match.destructured
            if (prefix.isNotEmpty() || endAmPm.isNotEmpty()) {
                val start = startH.toInt()
                val minute = startM.toIntOrNull() ?: 0
                val hour = when {
                    startAmPm.isNotEmpty() -> withMeridiem(start, startAmPm)
                    // "from 3 to 4pm" is 3 pm, but "from 11 to 1pm" starts at 11 am.
                    endAmPm == "pm" && start != 12 && start > endH.toInt() % 12 -> start
                    endAmPm.isNotEmpty() -> withMeridiem(start, endAmPm)
                    else -> bareHour(start, input)
                }
                if (hour in 0..23 && minute in 0..59) return LocalTime.of(hour, minute)
            }
        }

        namedTimeRegex.find(input)?.let {
            return if (it.groupValues[1] == "midnight") LocalTime.MIDNIGHT else LocalTime.NOON
        }

        // "at 8", "at 6:15", "at 12.30", "as 6:15" (speech recognition often hears "at" as "as")
        clockAtRegex.find(input)?.let { match ->
            val raw = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            val meridiem = match.groupValues[3]
            val hour = if (meridiem.isEmpty()) bareHour(raw, input) else withMeridiem(raw, meridiem)
            if (hour in 0..23 && minute in 0..59) return LocalTime.of(hour, minute)
        }

        // "8pm", "3:30 am"
        clockAmPmRegex.find(input)?.let { match ->
            val hour = withMeridiem(match.groupValues[1].toInt(), match.groupValues[3])
            val minute = match.groupValues[2].toIntOrNull() ?: 0
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

        // "31/12", "5/11/2026"
        numericDateRegex.find(input)?.let { match ->
            var day = match.groupValues[1].toInt()
            var month = match.groupValues[2].toInt()
            if (month > 12 && day <= 12) day = month.also { month = day }
            val year = match.groupValues[3].toIntOrNull()?.let { if (it < 100) 2000 + it else it }
            val date = runCatching { LocalDate.of(year ?: today.year, month, day) }.getOrNull()
            if (date != null) {
                return if (year == null && date.isBefore(today)) date.plusYears(1) else date
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
            .replace(rangeRegex) { m -> if (m.groupValues[1].isNotEmpty() || m.groupValues[7].isNotEmpty()) "" else m.value }
            .replace(Regex("""\b(?:at|as)\s+\d{1,2}([:.]\d{2})?\s*(am|pm)?"""), "")
            .replace(clockAmPmRegex, "")
            // Remove exactly the words taken as the person, not whatever word follows them.
            .let { text -> if (person != null) text.replace(Regex("""\bwith\s+${Regex.escape(person)}\b"""), "") else text }
            .replace(Regex("""\b(?:on\s+)?${numericDateRegex.pattern}"""), "")
            .replace(dayPartPhraseRegex, "")
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
