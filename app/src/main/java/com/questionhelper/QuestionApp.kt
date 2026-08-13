package com.questionhelper

import android.app.Application
import android.util.Log
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

        ocrManager = try {
            OcrManager(this).also {
                Log.d("QuestionApp", "OcrManager initialized, ready=${it.isReady}")
            }
        } catch (e: Throwable) {
            Log.e("QuestionApp", "Fatal: OcrManager init crashed: ${e.message}", e)
            // 创建一个不可用的实例，避免后续 NPE
            OcrManager(this)
        }
    }
}
