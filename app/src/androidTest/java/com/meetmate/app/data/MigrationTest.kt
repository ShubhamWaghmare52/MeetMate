package com.meetmate.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Checks that a version-1 database (the first release) upgrades without losing meetings. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val testDb = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MeetingDatabase::class.java
    )

    @Test
    fun migrate1To2KeepsDataAndAddsDefaults() {
        helper.createDatabase(testDb, 1).apply {
            execSQL(
                """INSERT INTO meetings (id, title, personName, dateTimeMillis, reminderMinutesBefore,
                   notes, category, isCompleted, createdAt)
                   VALUES (7, 'Lunch', 'Priya', 1000, 15, 'bring laptop', 'Work', 0, 1)"""
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 2, true)
        db.query("SELECT title, personName, notes, repeat, meetingLink, lastRemindedOccurrence FROM meetings WHERE id = 7").use {
            it.moveToFirst()
            assertEquals("Lunch", it.getString(0))
            assertEquals("Priya", it.getString(1))
            assertEquals("bring laptop", it.getString(2))
            assertEquals("NONE", it.getString(3))
            assertEquals("", it.getString(4))
            assertEquals(0L, it.getLong(5))
        }
    }
}
