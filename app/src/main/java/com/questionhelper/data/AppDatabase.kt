package com.questionhelper.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Question::class, UserRecord::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun questionDao(): QuestionDao
    abstract fun userRecordDao(): UserRecordDao
}
