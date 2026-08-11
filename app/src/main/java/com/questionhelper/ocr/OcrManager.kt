package com.questionhelper.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OcrManager(context: Context) {
    private var predictor: OCRPredictor? = null
    private val tag = "OcrManager"
    val isReady: Boolean
        get() = predictor != null

    init {
        initPredictor(context)
    }

    private fun initPredictor(context: Context) {
        try {
            val assetPath = "paddleocr"
            predictor = OCRPredictor(context, assetPath)
            Log.d(tag, "OCR initialized with PaddleOCR")
        } catch (e: Throwable) {
            Log.e(tag, "OCR Predictor init failed, will fallback to ML Kit", e)
            predictor = null
        }
    }

    /**
     * 识别图片中的文字
     * @return 识别结果文本，失败返回空字符串
     */
    suspend fun recognizeFromBitmap(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        if (predictor == null) {
            Log.w(tag, "PaddleOCR not available, skipping")
            return@withContext ""
        }
        try {
            val results = predictor!!.runOcr(bitmap)
            results.joinToString("\n") { it.text }
        } catch (e: Throwable) {
            Log.e(tag, "OCR recognition failed", e)
            ""
        }
    }

    fun close() {
        try {
            predictor?.release()
        } catch (e: Throwable) {
            Log.e(tag, "Release failed", e)
        } finally {
            predictor = null
        }
    }
}
