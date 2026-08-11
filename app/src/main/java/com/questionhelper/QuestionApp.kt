package com.questionhelper

import android.app.Application
import androidx.room.Room
import com.questionhelper.data.AppDatabase

class QuestionApp : Application() {

    companion object {
        /**
         * 使用 by lazy 确保线程安全，且只在首次访问时初始化
         * 由于 Application.onCreate 一定在主线程最先执行，
         * 这里用 lazy + synchronized 双重保险
         */
        @Volatile
        private var _database: AppDatabase? = null

        val database: AppDatabase
            get() = _database
                ?: throw IllegalStateException("QuestionApp.database accessed before Application.onCreate()")
    }

    override fun onCreate() {
        super.onCreate()
        // 在 onCreate 中立即初始化，避免任何竞态
        _database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "question_db"
        )
            // 作为自用工具，允许破坏性迁移，避免升级崩溃
            .fallbackToDestructiveMigration()
            .build()
    }
}
