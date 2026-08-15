package com.questionhelper.ocr

import android.content.Context
import android.graphics.Bitmap
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

    /** ML Kit 无需显式初始化，始终可用 */
    val isReady: Boolean = true

    /** 兼容旧代码，错误时可通过此字段获取信息 */
    var initError: String? = null

    suspend fun recognizeFromBitmap(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(image).await()
            val text = result.textBlocks.joinToString("\n") { block -> block.text }
            Log.d(tag, "ML Kit 识别完成，长度=${text.length}")
            text
        } catch (e: Exception) {
            Log.e(tag, "ML Kit 识别失败", e)
            initError = e.message
            ""
        }
    }

    fun close() {
        try { recognizer.close() } catch (_: Exception) {}
    }
}