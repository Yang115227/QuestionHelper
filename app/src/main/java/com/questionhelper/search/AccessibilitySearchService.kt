package com.questionhelper.search

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.questionhelper.QuestionApp
import com.questionhelper.data.QuestionRepository
import com.questionhelper.ocr.OcrManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AccessibilitySearchService : AccessibilityService() {
    private lateinit var ocrManager: OcrManager

    private val captureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "com.questionhelper.ACCESSIBILITY_CAPTURE") {
                val rect = intent.getParcelableExtra<Rect>("rect")
                rect?.let { captureWithAccessibility(it) }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        ocrManager = OcrManager(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            registerReceiver(captureReceiver, IntentFilter("com.questionhelper.ACCESSIBILITY_CAPTURE"),
                Context.RECEIVER_NOT_EXPORTED)
        }
        Toast.makeText(this, "无障碍搜题服务已启动", Toast.LENGTH_SHORT).show()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun captureWithAccessibility(rect: Rect) {
        takeScreenshot(Display.DEFAULT_DISPLAY, ContextCompat.getMainExecutor(this),
            object : TakeScreenshotCallback {
                override fun onCaptureSuccess(screenshotResult: ScreenshotResult) {
                    val bitmap = screenshotResult.bitmap
                    if (bitmap != null) {
                        try {
                            val cropped = Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height())
                            bitmap.recycle()
                            processBitmap(cropped)
                        } catch (e: Exception) {
                            Log.e("Accessibility", "Crop failed", e)
                        }
                    }
                }
                override fun onFailure(errorCode: Int) {
                    Log.e("Accessibility", "Screenshot failed: $errorCode")
                }
            }
        )
    }

    private fun processBitmap(bitmap: Bitmap) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val text = ocrManager.recognizeFromBitmap(bitmap)
                if (text.isNotEmpty()) {
                    val repo = QuestionRepository(QuestionApp.database.questionDao())
                    val question = repo.searchQuestion(text.take(50))
                    Handler(Looper.getMainLooper()).post {
                        val intent = Intent(this@AccessibilitySearchService, SearchResultActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            putExtra("question", question?.content ?: text)
                            putExtra("answer", question?.answer ?: "未找到匹配题目")
                            putExtra("analysis", question?.analysis ?: "")
                        }
                        startActivity(intent)
                    }
                }
            } catch (e: Exception) {
                Log.e("Accessibility", "Error", e)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            unregisterReceiver(captureReceiver)
        }
        ocrManager.close()
    }
}
