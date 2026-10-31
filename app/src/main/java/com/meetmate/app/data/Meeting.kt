package com.meetmate.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class Repeat(val label: String) {
    NONE("Never"),
    DAILY("Every day"),
    WEEKDAYS("Weekdays"),
    WEEKLY("Every week")
}

@Entity(tableName = "meetings")
data class Meeting(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val personName: String,
    /** First occurrence. For repeating meetings later occurrences are derived from it. */
    val dateTimeMillis: Long,
    val reminderMinutesBefore: Int = 30,
    val notes: String = "",
    val category: String = "General",
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "NONE")
    val repeat: Repeat = Repeat.NONE,
    @ColumnInfo(defaultValue = "")
    val meetingLink: String = "",
    /** Occurrence (epoch millis) whose reminder already rang, so re-scheduling doesn't ring it twice. */
    @ColumnInfo(defaultValue = "0")
    val lastRemindedOccurrence: Long = 0
)

val MeetingCategories = listOf("General", "Work", "Personal", "Call")
