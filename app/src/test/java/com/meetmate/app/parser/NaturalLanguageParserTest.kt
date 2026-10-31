package com.meetmate.app.parser

import com.meetmate.app.data.Repeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class NaturalLanguageParserTest {

    // Thursday 30 Oct 2025, 09:00
    private val today = LocalDate.of(2025, 10, 30)
    private val now = LocalTime.of(9, 0)

    private fun parse(text: String) = NaturalLanguageParser.parse(text, today, now)

    @Test
    fun `full sentence fills every field`() {
        val p = parse("Lunch with Priya tomorrow at 1pm")
        assertEquals("Priya", p.personName)
        assertEquals(today.plusDays(1), p.date)
        assertEquals(LocalTime.of(13, 0), p.time)
        assertEquals("Lunch", p.title)
    }

    @Test
    fun `two word names are capitalised`() {
        assertEquals("Rahul Sharma", parse("call with rahul sharma at 5pm").personName)
    }

    @Test
    fun `stop words after with are not a person`() {
        assertNull(parse("meeting with the team at 3pm").personName)
    }

    @Test
    fun `day after tomorrow is two days out`() {
        assertEquals(today.plusDays(2), parse("dentist day after tomorrow at 4pm").date)
    }

    @Test
    fun `weekday resolves to next occurrence`() {
        assertEquals(LocalDate.of(2025, 11, 3), parse("standup at 10am on monday").date)
    }

    @Test
    fun `same weekday means today`() {
        assertEquals(today, parse("review on thursday at 4pm").date)
    }

    @Test
    fun `day of month in the past rolls to next month`() {
        assertEquals(LocalDate.of(2025, 11, 15), parse("sync on 15th at 11am").date)
        assertEquals(LocalDate.of(2025, 10, 31), parse("sync on 31st at 11am").date)
    }

    @Test
    fun `bare small hour is treated as afternoon`() {
        assertEquals(LocalTime.of(17, 0), parse("call at 5").time)
        assertEquals(LocalTime.of(9, 30), parse("call at 9:30").time)
    }

    @Test
    fun `am and pm are respected`() {
        assertEquals(LocalTime.of(0, 15), parse("deploy at 12:15am").time)
        assertEquals(LocalTime.of(12, 0), parse("lunch 12pm").time)
        assertEquals(LocalTime.of(18, 30), parse("call 6:30pm").time)
    }

    @Test
    fun `voice misheard as is accepted`() {
        assertEquals(LocalTime.of(18, 15), parse("call as 6:15pm").time)
    }

    @Test
    fun `time only uses today when still ahead, else tomorrow`() {
        assertEquals(today, parse("gym at 6pm").date)
        assertEquals(today.plusDays(1), parse("gym at 8am").date)
    }

    @Test
    fun `missing time asks for the picker`() {
        val p = parse("project kickoff")
        assertNull(p.time)
        assertTrue(p.needsTimePicker)
        assertEquals("Project kickoff", p.title)
    }

    @Test
    fun `date words are stripped from the title`() {
        assertEquals("Coffee", parse("coffee tomorrow at 10am").title)
        assertEquals("Retro", parse("retro next friday at 3pm").title)
    }

    @Test
    fun `empty title falls back to person`() {
        assertEquals("Meeting with Alex", parse("with alex at 6pm").title)
    }

    @Test
    fun `noon and midnight`() {
        assertEquals(LocalTime.NOON, parse("call with alex at noon").time)
        assertEquals(LocalTime.MIDNIGHT, parse("deploy at midnight").time)
        assertEquals("Call", parse("call with alex at noon").title)
    }

    @Test
    fun `relative times`() {
        val p = parse("gym in 2 hours")
        assertEquals(today, p.date)
        assertEquals(LocalTime.of(11, 0), p.time)
        assertEquals("Gym", p.title)
        assertEquals(LocalTime.of(9, 30), parse("call in half an hour").time)
        assertEquals(LocalTime.of(9, 45), parse("tea in 45 mins").time)
        assertEquals(LocalTime.of(10, 0), parse("sync in an hour").time)
    }

    @Test
    fun `relative time can cross midnight`() {
        val p = NaturalLanguageParser.parse("call in 2 hours", today, LocalTime.of(23, 30))
        assertEquals(today.plusDays(1), p.date)
        assertEquals(LocalTime.of(1, 30), p.time)
    }

    @Test
    fun `month names`() {
        assertEquals(LocalDate.of(2025, 11, 5), parse("review on 5 nov at 11am").date)
        assertEquals(LocalDate.of(2025, 11, 5), parse("review november 5th at 11am").date)
        assertEquals(LocalDate.of(2025, 12, 25), parse("party on the 25th of december").date)
        assertEquals("Review", parse("review on 5 nov at 11am").title)
    }

    @Test
    fun `past month date rolls to next year`() {
        assertEquals(LocalDate.of(2026, 3, 1), parse("audit 1 march at 10am").date)
    }

    @Test
    fun `next weekday is strictly after today`() {
        // today is Thursday
        assertEquals(LocalDate.of(2025, 11, 6), parse("demo next thursday at 3pm").date)
        assertEquals(today, parse("demo this thursday at 3pm").date)
    }

    @Test
    fun `next week`() {
        assertEquals(today.plusWeeks(1), parse("planning next week at 10am").date)
        assertEquals("Planning", parse("planning next week at 10am").title)
    }

    @Test
    fun `repeat phrases`() {
        val standup = parse("standup every weekday at 10am")
        assertEquals(Repeat.WEEKDAYS, standup.repeat)
        assertEquals("Standup", standup.title)
        assertEquals(Repeat.DAILY, parse("meds daily at 9pm").repeat)
        val oneOnOne = parse("1:1 with sam every monday at 4pm")
        assertEquals(Repeat.WEEKLY, oneOnOne.repeat)
        assertEquals(LocalDate.of(2025, 11, 3), oneOnOne.date)
        assertNull(parse("lunch tomorrow at 1pm").repeat)
    }

    @Test
    fun `meeting links are extracted and kept out of the title`() {
        val p = parse("Design review at 3pm https://meet.google.com/abc-defg-hij")
        assertEquals("https://meet.google.com/abc-defg-hij", p.meetingLink)
        assertEquals("Design review", p.title)
        assertEquals("zoom.us/j/123", parse("sync zoom.us/j/123 at 2pm").meetingLink)
    }

    @Test
    fun `autocorrected and spoken links are still found`() {
        // What Gboard actually produced on a Pixel for "meet.google.com/abc-defg-hij"
        val typed = parse("Alarm test with Sam in 2 minutes meet. Google. Com/abc-defg-hij")
        assertEquals("meet.google.com/abc-defg-hij", typed.meetingLink)
        assertEquals("Alarm test", typed.title)
        assertEquals("Sam", typed.personName)
        assertEquals(
            "meet.google.com/xyz-abcd-efg",
            parse("sync at 4pm meet dot google dot com slash xyz-abcd-efg".replace(" slash ", "/")).meetingLink
        )
        assertEquals("https://zoom.us/j/AbC", parse("call https://Zoom.us/j/AbC at 5pm").meetingLink)
    }

    @Test
    fun `at inside a word is not a time`() {
        assertEquals("Chat", parse("chat 5pm").title)
    }

    @Test
    fun `minutes after a dot`() {
        val p = parse("lunch next tuesday at 12.30")
        assertEquals(LocalTime.of(12, 30), p.time)
        assertEquals(LocalDate.of(2025, 11, 4), p.date)
        assertEquals("Lunch", p.title)
        assertEquals(LocalTime.of(18, 45), parse("call 6.45pm").time)
    }

    @Test
    fun `numeric dates are day first`() {
        val p = parse("review at 3 pm on 31/12")
        assertEquals(LocalDate.of(2025, 12, 31), p.date)
        assertEquals(LocalTime.of(15, 0), p.time)
        assertEquals("Review", p.title)
        assertEquals(LocalDate.of(2026, 11, 5), parse("sync 5/11/2026 at 10am").date)
        assertEquals(LocalDate.of(2026, 11, 5), parse("sync 5/11/26 at 10am").date)
        // Only month-first makes sense here
        assertEquals(LocalDate.of(2025, 12, 25), parse("party 12/25 at 7pm").date)
    }

    @Test
    fun `past numeric date rolls to next year`() {
        assertEquals(LocalDate.of(2026, 3, 1), parse("audit 1/3 at 10am").date)
    }

    @Test
    fun `words for the time of day pick am or pm`() {
        val dinner = parse("dinner with alex at 8")
        assertEquals(LocalTime.of(20, 0), dinner.time)
        assertEquals("Dinner", dinner.title)
        assertEquals("Alex", dinner.personName)
        val gym = parse("gym at 6 in the morning")
        assertEquals(LocalTime.of(6, 0), gym.time)
        assertEquals("Gym", gym.title)
        assertEquals(LocalTime.of(19, 0), parse("call at 7 this evening").time)
        assertEquals(LocalTime.of(9, 0), parse("morning standup at 9").time)
    }

    @Test
    fun `time ranges use the start`() {
        val p = parse("meeting from 3 to 4pm")
        assertEquals(LocalTime.of(15, 0), p.time)
        assertEquals("Meeting", p.title)
        assertEquals(LocalTime.of(11, 0), parse("review from 11 to 1pm").time)
        assertEquals(LocalTime.of(14, 0), parse("sync 2-3pm").time)
        assertEquals("Sync", parse("sync 2-3pm").title)
        assertEquals(LocalTime.of(10, 0), parse("workshop between 10 and 11am").time)
        assertEquals(LocalTime.of(9, 30), parse("call from 9:30 to 10:15am").time)
    }

    @Test
    fun `a one to one is not a range`() {
        val p = parse("1 to 1 with sam at 4pm")
        assertEquals(LocalTime.of(16, 0), p.time)
        assertEquals("Sam", p.personName)
        assertEquals("1 to 1", p.title)
    }

    @Test
    fun `every weekday never starts on a weekend`() {
        // Saturday 1 Nov 2025, after 10:00: "tomorrow" would be Sunday
        val p = NaturalLanguageParser.parse("standup every weekday at 10am", LocalDate.of(2025, 11, 1), LocalTime.of(11, 0))
        assertEquals(LocalDate.of(2025, 11, 3), p.date)
        assertEquals(LocalDate.of(2025, 11, 3), NaturalLanguageParser.parse("standup every weekday at 10am", LocalDate.of(2025, 11, 2), LocalTime.of(8, 0)).date)
    }
}
