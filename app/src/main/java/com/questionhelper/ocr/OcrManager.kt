package com.questionhelper.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class OcrManager(private val appContext: Context) {

    private val tag = "OcrManager"
    private val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    val isReady: Boolean = true
    var initError: String? = null

    suspend fun recognizeFromBitmap(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        try {
            // 图像预处理：灰度化 + 对比度增强 + 条件放大
            val processedBitmap = preprocessBitmap(bitmap)
            val image = InputImage.fromBitmap(processedBitmap, 0)
            val result = recognizer.process(image).await()
            val text = result.textBlocks.joinToString("\n") { block -> block.text }
            Log.d(tag, "ML Kit 识别完成，长度=${text.length}")

            // 释放处理的 Bitmap（如果与原始不同）
            if (processedBitmap != bitmap) {
                processedBitmap.recycle()
            }
            text
        } catch (e: Exception) {
            Log.e(tag, "ML Kit 识别失败", e)
            initError = e.message
            ""
        }
    }

    /**
     * 图像预处理：
     * 1. 灰度化
     * 2. 对比度增强（温和参数）
     * 3. 如果文字区域较小，则放大；否则保持原尺寸
     */
    private fun preprocessBitmap(src: Bitmap): Bitmap {
        // 1. 灰度化
        val grayscale = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(grayscale)
        val paint = Paint()
        val colorMatrix = ColorMatrix().apply {
            setSaturation(0f)
        }
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(src, 0f, 0f, paint)

        // 2. 对比度增强（采用更温和的系数，避免过度增强导致噪点）
        val contrastMatrix = ColorMatrix().apply {
            val scale = 1.2f
            val translate = -20f
            set(
                floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        }
        val contrastPaint = Paint()
        contrastPaint.colorFilter = ColorMatrixColorFilter(contrastMatrix)
        val enhanced = Bitmap.createBitmap(grayscale.width, grayscale.height, grayscale.config)
        val enhancedCanvas = Canvas(enhanced)
        enhancedCanvas.drawBitmap(grayscale, 0f, 0f, contrastPaint)

        if (grayscale != src && grayscale != enhanced) {
            grayscale.recycle()
        }

        // 3. 条件放大：只有当文字区域高度小于 200 像素时才放大 1.5 倍，
        //    否则保持原尺寸，避免放大导致模糊。
        val targetMinHeight = 200
        val scaleFactor = if (enhanced.height < targetMinHeight) 1.5f else 1.0f

        val finalBitmap = if (scaleFactor > 1.0f) {
            val scaledWidth = (enhanced.width * scaleFactor).toInt()
            val scaledHeight = (enhanced.height * scaleFactor).toInt()
            Bitmap.createScaledBitmap(enhanced, scaledWidth, scaledHeight, true)
        } else {
            enhanced
        }

        if (enhanced != finalBitmap) {
            enhanced.recycle()
        }

        return finalBitmap
    }

    fun close() {
        try {
            recognizer.close()
        } catch (_: Exception) {
        }
    }
}