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

    @Query("SELECT * FROM meetings WHERE isCompleted = 0 ORDER BY dateTimeMillis ASC")
    fun getUpcomingMeetings(): Flow<List<Meeting>>

    @Query("SELECT * FROM meetings ORDER BY dateTimeMillis ASC")
    fun getAllMeetings(): Flow<List<Meeting>>

    @Query("SELECT * FROM meetings WHERE isCompleted = 1 ORDER BY dateTimeMillis DESC")
    fun getPastMeetings(): Flow<List<Meeting>>

    @Query("SELECT * FROM meetings WHERE dateTimeMillis BETWEEN :startOfDay AND :endOfDay AND isCompleted = 0 ORDER BY dateTimeMillis ASC")
    fun getTodayMeetings(startOfDay: Long, endOfDay: Long): Flow<List<Meeting>>

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

    @Query("UPDATE meetings SET isCompleted = 1 WHERE id = :id")
    suspend fun markCompleted(id: Long)

    @Query("SELECT * FROM meetings WHERE personName LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%' ORDER BY dateTimeMillis DESC")
    fun search(query: String): Flow<List<Meeting>>
}
