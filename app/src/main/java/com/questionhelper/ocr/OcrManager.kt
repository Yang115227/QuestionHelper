package com.questionhelper.ocr

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OcrManager(context: Context) {
    private var predictor: OCRPredictor? = null
    private val tag = "OcrManager"
    
    /** 初始化失败的详细原因，UI 层可直接读取显示 */
    var initError: String? = null
        private set

    val isReady: Boolean
        get() = predictor != null

    init {
        initPredictor(context)
    }

    private fun initPredictor(context: Context) {
        val sb = StringBuilder()
        
        // 1. 检查 assets 模型文件
        val assetManager = context.assets
        val requiredFiles = listOf(
            "paddleocr/ch_PP-OCRv3_det_infer_opt.nb",
            "paddleocr/ch_PP-OCRv3_rec_infer_opt.nb",
            "paddleocr/ch_ppocr_mobile_v2.0_cls_infer_opt.nb",
            "paddleocr/ppocr_keys_v1.txt"
        )
        var missingAssets = 0
        for (file in requiredFiles) {
            try {
                assetManager.open(file).close()
            } catch (e: Exception) {
                missingAssets++
                sb.append("缺少模型: $file\n")
            }
        }
        if (missingAssets > 0) {
            initError = "模型文件缺失 ($missingAssets 个)\n$sb"
            Log.e(tag, initError!!)
            showToast(context, initError!!)
            return
        }

        // 2. 检查类是否存在（确认 JAR 生效，而非 stub）
        try {
            Class.forName("com.baidu.paddle.lite.PaddlePredictor")
        } catch (e: Throwable) {
            initError = "PaddlePredictor 类未找到\n请确认 PaddlePredictor.jar 已打包进 APK，且 stub 已删除"
            Log.e(tag, initError!!, e)
            showToast(context, initError!!)
            return
        }

        // 3. 显式加载 native SO
        try {
            System.loadLibrary("paddle_lite_jni")
            Log.d(tag, "SO 加载成功")
        } catch (e: UnsatisfiedLinkError) {
            initError = "SO 加载失败: ${e.message}\n请确认 APK 包含 lib/arm64-v8a/libpaddle_lite_jni.so"
            Log.e(tag, initError!!, e)
            showToast(context, initError!!)
            return
        }

        // 4. 初始化预测器
        try {
            predictor = OCRPredictor(context, "paddleocr")
            initError = null
            Log.d(tag, "✅ OCR 初始化成功")
        } catch (e: Throwable) {
            val cause = e.cause ?: e
            initError = "预测器初始化失败\n${cause.javaClass.simpleName}: ${cause.message}"
            Log.e(tag, initError!!, e)
            showToast(context, initError!!)
        }
    }

    private fun showToast(context: Context, msg: String) {
        // 在主线程显示 Toast，让用户直接看到错误
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, "OCR诊断: $msg", Toast.LENGTH_LONG).show()
        }
    }

    suspend fun recognizeFromBitmap(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        val p = predictor
        if (p == null) {
            Log.w(tag, "predictor is null, error=$initError")
            return@withContext ""
        }
        try {
            val results = p.runOcr(bitmap)
            results.joinToString("\n") { it.text }
        } catch (e: Throwable) {
            Log.e(tag, "识别失败: ${e.message}", e)
            ""
        }
    }

    fun close() {
        try { predictor?.release() } catch (_: Throwable) {}
        predictor = null
    }
}
