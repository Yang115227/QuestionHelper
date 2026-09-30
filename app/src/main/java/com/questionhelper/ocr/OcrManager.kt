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
            // 图像预处理
            val processedBitmap = preprocessBitmap(bitmap)
            val image = InputImage.fromBitmap(processedBitmap, 0)
            val result = recognizer.process(image).await()

            // 拼接文本，并清理常见的识别错误
            var text = result.textBlocks.joinToString("\n") { block -> block.text }
            text = cleanOcrText(text)

            Log.d(tag, "ML Kit 识别完成，长度=${text.length}")

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
     * 清理 OCR 文本：
     * - 统一引号、括号
     * - 去除多余空白
     */
    private fun cleanOcrText(text: String): String {
        return text
            .replace("“", "\"")
            .replace("”", "\"")
            .replace("‘", "'")
            .replace("’", "'")
            .replace("（", "(")
            .replace("）", ")")
            .replace(Regex("[ \t]+"), " ")
            .replace(Regex("\n{2,}"), "\n")
            .trim()
    }

    /**
     * 图像预处理：
     * 1. 灰度化
     * 2. 对比度增强
     * 3. 根据图像大小条件放大
     */
    private fun preprocessBitmap(src: Bitmap): Bitmap {
        // 1. 灰度化
        val grayscale = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(grayscale)
        val paint = Paint()
        val colorMatrix = ColorMatrix().apply { setSaturation(0f) }
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(src, 0f, 0f, paint)

        // 2. 对比度增强（温和参数）
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

        // 3. 条件放大：仅当高度小于 200 时放大 1.5 倍
        val targetMinHeight = 200
        val scaleFactor = if (enhanced.height < targetMinHeight) 1.5f else 1.0f

        val finalBitmap = if (scaleFactor > 1.0f) {
            Bitmap.createScaledBitmap(
                enhanced,
                (enhanced.width * scaleFactor).toInt(),
                (enhanced.height * scaleFactor).toInt(),
                true
            )
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