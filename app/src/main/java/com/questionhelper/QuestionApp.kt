package com.questionhelper

import android.app.Application
import com.questionhelper.ocr.OcrManager

class QuestionApp : Application() {
    companion object {
        lateinit var ocrManager: OcrManager
            private set
    }

    override fun onCreate() {
        super.onCreate()
        ocrManager = OcrManager(this)
    }
}