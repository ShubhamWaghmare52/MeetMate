package com.meetmate.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "meetings")
data class Meeting(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val personName: String,
    val dateTimeMillis: Long,
    val reminderMinutesBefore: Int = 30,
    val notes: String = "",
    val category: String = "General",
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
