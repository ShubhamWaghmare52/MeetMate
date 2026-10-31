package com.meetmate.app.data

/** What the add/edit screen hands back on save. */
data class MeetingDraft(
    val title: String,
    val personName: String,
    val dateTimeMillis: Long,
    val reminderMinutesBefore: Int,
    val notes: String,
    val category: String,
    val repeat: Repeat,
    val meetingLink: String
)
