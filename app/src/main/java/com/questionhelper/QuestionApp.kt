package com.questionhelper

import android.app.Application
import androidx.room.Room
import com.questionhelper.data.AppDatabase
import com.questionhelper.ocr.OcrManager

class QuestionApp : Application() {
    companion object {
        lateinit var database: AppDatabase
            private set
        lateinit var ocrManager: OcrManager
            private set
    }

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "question_db"
        ).build()

        // 应用启动时预初始化 OCR（耗时操作，避免截图时才创建）
        ocrManager = OcrManager(this)
    }
}

