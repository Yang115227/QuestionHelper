package com.questionhelper

import android.app.Application
import androidx.room.Room
import com.questionhelper.data.AppDatabase

class QuestionApp : Application() {
    companion object {
        lateinit var database: AppDatabase
            private set
    }

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "question_db"
        ).build()
    }
}
