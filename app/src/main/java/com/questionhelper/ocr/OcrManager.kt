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
        // 1. 检查 assets 模型文件
        val assetManager = context.assets
        val requiredFiles = listOf(
            "paddleocr/ch_PP-OCRv3_det_infer_opt.nb",
            "paddleocr/ch_PP-OCRv3_rec_infer_opt.nb",
            "paddleocr/ch_ppocr_mobile_v2.0_cls_infer_opt.nb",
            "paddleocr/ppocr_keys_v1.txt"
        )
        for (file in requiredFiles) {
            try {
                assetManager.open(file).close()
                Log.d(tag, "✅ Asset exists: $file")
            } catch (e: Exception) {
                Log.e(tag, "❌ Asset MISSING: $file")
            }
        }

        // 2. 尝试显式加载 native SO（Paddle Lite 依赖）
        try {
            System.loadLibrary("paddle_lite_jni")
            Log.d(tag, "✅ libpaddle_lite_jni.so loaded")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(tag, "❌ Failed to load libpaddle_lite_jni.so: ${e.message}")
            Log.e(tag, "   Hint: Check if APK contains lib/arm64-v8a/libpaddle_lite_jni.so")
            predictor = null
            return
        } catch (e: Throwable) {
            Log.e(tag, "❌ Unexpected error loading SO: ${e.message}")
            predictor = null
            return
        }

        // 3. 初始化 PaddleOCR 预测器
        try {
            val assetPath = "paddleocr"
            predictor = OCRPredictor(context, assetPath)
            Log.d(tag, "✅ OCR initialized with PaddleOCR")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(tag, "❌ Native method not found (SO/JAR version mismatch): ${e.message}", e)
            predictor = null
        } catch (e: ExceptionInInitializerError) {
            Log.e(tag, "❌ Native class init failed: ${e.message}", e)
            predictor = null
        } catch (e: NoClassDefFoundError) {
            Log.e(tag, "❌ PaddlePredictor class not found (stub compiled instead of JAR?): ${e.message}", e)
            predictor = null
        } catch (e: Exception) {
            Log.e(tag, "❌ OCR Predictor init failed (${e.javaClass.simpleName}): ${e.message}", e)
            predictor = null
        }
    }

    suspend fun recognizeFromBitmap(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        if (predictor == null) {
            Log.w(tag, "⚠️ PaddleOCR not available, skipping")
            return@withContext ""
        }
        try {
            val results = predictor!!.runOcr(bitmap)
            results.joinToString("\n") { it.text }
        } catch (e: Throwable) {
            Log.e(tag, "❌ OCR recognition failed: ${e.message}", e)
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
