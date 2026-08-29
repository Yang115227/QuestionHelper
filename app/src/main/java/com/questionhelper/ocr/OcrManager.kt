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
            // 图像预处理：灰度化 + 对比度增强
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
     * 图像预处理：转换为灰度图，增强对比度，并适当放大（可选）
     */
    private fun preprocessBitmap(src: Bitmap): Bitmap {
        // 1. 转换为灰度图
        val grayscale = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(grayscale)
        val paint = Paint()
        val colorMatrix = ColorMatrix().apply {
            setSaturation(0f) // 饱和度设为0，即灰度
        }
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(src, 0f, 0f, paint)

        // 2. 增强对比度
        val contrastMatrix = ColorMatrix().apply {
            val scale = 1.5f  // 对比度增强系数，可调整
            val translate = (-0.25f * 255).toInt()
            set(floatArrayOf(
                scale, 0f, 0f, 0f, translate.toFloat(),
                0f, scale, 0f, 0f, translate.toFloat(),
                0f, 0f, scale, 0f, translate.toFloat(),
                0f, 0f, 0f, 1f, 0f
            ))
        }
        val contrastPaint = Paint()
        contrastPaint.colorFilter = ColorMatrixColorFilter(contrastMatrix)
        val enhanced = Bitmap.createBitmap(grayscale.width, grayscale.height, grayscale.config)
        val enhancedCanvas = Canvas(enhanced)
        enhancedCanvas.drawBitmap(grayscale, 0f, 0f, contrastPaint)

        if (grayscale != src && grayscale != enhanced) {
            grayscale.recycle()
        }

        // 3. 可选：放大图像（如果文字较小）
        val scaleFactor = 1.5f  // 放大倍数，可根据需要调整
        val scaledWidth = (enhanced.width * scaleFactor).toInt()
        val scaledHeight = (enhanced.height * scaleFactor).toInt()
        val scaledBitmap = Bitmap.createScaledBitmap(enhanced, scaledWidth, scaledHeight, true)

        if (enhanced != scaledBitmap) {
            enhanced.recycle()
        }

        return scaledBitmap
    }

    fun close() {
        try { recognizer.close() } catch (_: Exception) {}
    }
}