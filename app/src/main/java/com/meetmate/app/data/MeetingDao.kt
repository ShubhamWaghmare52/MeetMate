package com.meetmate.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MeetingDao {

    @Query("SELECT * FROM meetings ORDER BY dateTimeMillis ASC")
    fun getAllMeetings(): Flow<List<Meeting>>

    @Query("SELECT * FROM meetings WHERE id = :id")
    suspend fun getMeetingById(id: Long): Meeting?

    @Insert
    suspend fun insert(meeting: Meeting): Long

    @Update
    suspend fun update(meeting: Meeting)

    @Upsert
    suspend fun upsert(meeting: Meeting)

    @Delete
    suspend fun delete(meeting: Meeting)

    @Query("UPDATE meetings SET lastRemindedOccurrence = :occurrence WHERE id = :id")
    suspend fun markReminded(id: Long, occurrence: Long)

    @Query("SELECT * FROM meetings WHERE isCompleted = 0")
    suspend fun getActiveMeetings(): List<Meeting>

    @Query("UPDATE meetings SET isCompleted = 1 WHERE id = :id")
    suspend fun markCompleted(id: Long)
}
