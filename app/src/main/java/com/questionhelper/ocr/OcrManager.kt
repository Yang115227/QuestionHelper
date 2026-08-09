package com.questionhelper.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OcrManager(context: Context) {
    private val predictor: OCRPredictor
    private val tag = "OcrManager"

    init {
        val assetPath = "paddleocr"
        predictor = OCRPredictor(context, assetPath)
        Log.d(tag, "OCR initialized with PaddleOCR")
    }

    suspend fun recognizeFromBitmap(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        try {
            val results = predictor.runOcr(bitmap)
            results.joinToString("\n") { it.text }
        } catch (e: Exception) {
            Log.e(tag, "OCR failed", e)
            ""
        }
    }

    fun close() {
        predictor.release()
    }
}
