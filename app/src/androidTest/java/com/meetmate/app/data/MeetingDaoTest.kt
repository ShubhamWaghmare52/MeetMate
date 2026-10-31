package com.meetmate.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs against an in-memory database, so it never touches the real app data. */
@RunWith(AndroidJUnit4::class)
class MeetingDaoTest {

    private lateinit var db: MeetingDatabase
    private lateinit var dao: MeetingDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MeetingDatabase::class.java).build()
        dao = db.meetingDao()
    }

    @After
    fun tearDown() = db.close()

    private fun meeting(title: String, at: Long) =
        Meeting(title = title, personName = "", dateTimeMillis = at)

    @Test
    fun allMeetingsAreSortedByTime() = runTest {
        dao.insert(meeting("late", 300))
        dao.insert(meeting("early", 100))
        assertEquals(listOf("early", "late"), dao.getAllMeetings().first().map { it.title })
    }

    @Test
    fun newFieldsRoundTrip() = runTest {
        val id = dao.insert(
            meeting("standup", 100).copy(
                repeat = Repeat.WEEKDAYS,
                meetingLink = "https://meet.google.com/x",
                notes = "agenda",
                category = "Work"
            )
        )
        val saved = dao.getMeetingById(id)!!
        assertEquals(Repeat.WEEKDAYS, saved.repeat)
        assertEquals("https://meet.google.com/x", saved.meetingLink)
        assertEquals("agenda", saved.notes)
        assertEquals("Work", saved.category)
    }

    @Test
    fun completedMeetingsAreNotActive() = runTest {
        val a = dao.insert(meeting("a", 100))
        dao.insert(meeting("b", 200))
        dao.markCompleted(a)
        assertEquals(listOf("b"), dao.getActiveMeetings().map { it.title })
    }

    @Test
    fun markRemindedStoresOccurrence() = runTest {
        val id = dao.insert(meeting("a", 100))
        dao.markReminded(id, 100)
        assertEquals(100L, dao.getMeetingById(id)!!.lastRemindedOccurrence)
    }

    @Test
    fun upsertRestoresDeletedMeetingWithSameId() = runTest {
        val id = dao.insert(meeting("a", 100))
        val original = dao.getMeetingById(id)!!
        dao.delete(original)
        assertNull(dao.getMeetingById(id))
        dao.upsert(original)
        assertTrue(dao.getMeetingById(id) == original)
    }
}
