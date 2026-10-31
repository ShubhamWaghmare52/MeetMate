package com.meetmate.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meetmate.app.data.Meeting
import com.meetmate.app.data.MeetingDatabase
import com.meetmate.app.data.MeetingDraft
import com.meetmate.app.notification.NotificationHelper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MeetingViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = MeetingDatabase.getDatabase(application).meetingDao()

    val allMeetings = dao.getAllMeetings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Alarms vanish after a force-stop; opening the app puts them back.
        viewModelScope.launch { NotificationHelper.rescheduleAll(getApplication()) }
    }

    fun addMeeting(draft: MeetingDraft) {
        viewModelScope.launch {
            val meeting = Meeting(
                title = draft.title,
                personName = draft.personName,
                dateTimeMillis = draft.dateTimeMillis,
                reminderMinutesBefore = draft.reminderMinutesBefore,
                notes = draft.notes,
                category = draft.category,
                repeat = draft.repeat,
                meetingLink = draft.meetingLink
            )
            val id = dao.insert(meeting)
            NotificationHelper.scheduleReminder(getApplication(), meeting.copy(id = id))
        }
    }

    fun updateMeeting(meeting: Meeting, draft: MeetingDraft) {
        viewModelScope.launch {
            val timingChanged = meeting.dateTimeMillis != draft.dateTimeMillis ||
                meeting.repeat != draft.repeat ||
                meeting.reminderMinutesBefore != draft.reminderMinutesBefore
            val updated = meeting.copy(
                title = draft.title,
                personName = draft.personName,
                dateTimeMillis = draft.dateTimeMillis,
                reminderMinutesBefore = draft.reminderMinutesBefore,
                notes = draft.notes,
                category = draft.category,
                repeat = draft.repeat,
                meetingLink = draft.meetingLink,
                // New timing means the next occurrence should ring even if an old one already did.
                lastRemindedOccurrence = if (timingChanged) 0 else meeting.lastRemindedOccurrence
            )
            dao.update(updated)
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
            NotificationHelper.scheduleReminder(getApplication(), meeting)
        }
    }

    /** For a repeating meeting this ends the whole series. */
    fun completeMeeting(meeting: Meeting) {
        viewModelScope.launch {
            dao.markCompleted(meeting.id)
            NotificationHelper.cancelReminder(getApplication(), meeting.id)
        }
    }
}
