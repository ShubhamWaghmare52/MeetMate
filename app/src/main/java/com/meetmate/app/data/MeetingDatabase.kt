package com.meetmate.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Meeting::class], version = 1, exportSchema = true)
abstract class MeetingDatabase : RoomDatabase() {

    abstract fun meetingDao(): MeetingDao

    companion object {
        @Volatile
        private var INSTANCE: MeetingDatabase? = null

        fun getDatabase(context: Context): MeetingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MeetingDatabase::class.java,
                    "meetmate_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
