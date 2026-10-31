package com.meetmate.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meetmate.app.data.Meeting
import com.meetmate.app.data.MeetingDatabase
import com.meetmate.app.notification.NotificationHelper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MeetingViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = MeetingDatabase.getDatabase(application).meetingDao()

    val allMeetings = dao.getAllMeetings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addMeeting(
        title: String,
        personName: String,
        dateTimeMillis: Long,
        reminderMinutesBefore: Int
    ) {
        viewModelScope.launch {
            val meeting = Meeting(
                title = title,
                personName = personName,
                dateTimeMillis = dateTimeMillis,
                reminderMinutesBefore = reminderMinutesBefore
            )
            val id = dao.insert(meeting)
            val savedMeeting = meeting.copy(id = id)
            NotificationHelper.scheduleReminder(getApplication(), savedMeeting)
        }
    }

    fun updateMeeting(
        meeting: Meeting,
        title: String,
        personName: String,
        dateTimeMillis: Long,
        reminderMinutesBefore: Int
    ) {
        viewModelScope.launch {
            val updated = meeting.copy(
                title = title,
                personName = personName,
                dateTimeMillis = dateTimeMillis,
                reminderMinutesBefore = reminderMinutesBefore
            )
            dao.update(updated)
            NotificationHelper.cancelReminder(getApplication(), meeting.id)
            NotificationHelper.scheduleReminder(getApplication(), updated)
        }
    }

    fun getMeetingById(id: Long, onResult: (Meeting?) -> Unit) {
        viewModelScope.launch {
            onResult(dao.getMeetingById(id))
        }
    }

    fun deleteMeeting(meeting: Meeting) {
        viewModelScope.launch {
            dao.delete(meeting)
            NotificationHelper.cancelReminder(getApplication(), meeting.id)
        }
    }

    /** Puts a deleted or completed meeting back exactly as it was (used by "Undo"). */
    fun restoreMeeting(meeting: Meeting) {
        viewModelScope.launch {
            dao.upsert(meeting)
            if (!meeting.isCompleted) {
                NotificationHelper.scheduleReminder(getApplication(), meeting)
            }
        }
    }

    fun completeMeeting(meeting: Meeting) {
        viewModelScope.launch {
            dao.markCompleted(meeting.id)
            NotificationHelper.cancelReminder(getApplication(), meeting.id)
        }
    }
}
