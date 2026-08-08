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
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.questionhelper.QuestionApp
import com.questionhelper.data.QuestionRepository
import com.questionhelper.ocr.OcrManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AccessibilitySearchService : AccessibilityService() {
    private lateinit var ocrManager: OcrManager
    private val handler = Handler(Looper.getMainLooper())
    private val TAG = "AccessibilitySearch"

    private val captureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "com.questionhelper.ACCESSIBILITY_CAPTURE") {
                val rect = intent.getParcelableExtra<Rect>("rect")
                Log.d(TAG, "Received capture request: $rect")
                rect?.let { captureWithAccessibility(it) }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        ocrManager = OcrManager(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val filter = IntentFilter("com.questionhelper.ACCESSIBILITY_CAPTURE")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(captureReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(captureReceiver, filter)
            }
        }
        Toast.makeText(this, "无障碍搜题服务已启动", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Accessibility service connected")
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun captureWithAccessibility(rect: Rect) {
        takeScreenshot(Display.DEFAULT_DISPLAY, handler) { screenshot ->
            screenshot?.let { bitmap ->
                try {
                    val safeRect = Rect(
                        rect.left.coerceIn(0, bitmap.width),
                        rect.top.coerceIn(0, bitmap.height),
                        rect.right.coerceIn(0, bitmap.width),
                        rect.bottom.coerceIn(0, bitmap.height)
                    )
                    
                    if (safeRect.width() > 0 && safeRect.height() > 0) {
                        val cropped = Bitmap.createBitmap(bitmap, safeRect.left, safeRect.top, safeRect.width(), safeRect.height())
                        bitmap.recycle()
                        processBitmap(cropped)
                    } else {
                        Log.e(TAG, "Invalid rect: $safeRect")
                        bitmap.recycle()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Crop failed", e)
                    bitmap.recycle()
                }
            } ?: run {
                Log.e(TAG, "Screenshot returned null")
                handler.post {
                    Toast.makeText(this@AccessibilitySearchService, "截图失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun processBitmap(bitmap: Bitmap) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val text = ocrManager.recognizeFromBitmap(bitmap)
                bitmap.recycle()
                
                if (text.isNotEmpty()) {
                    val repo = QuestionRepository(QuestionApp.database.questionDao())
                    val question = repo.searchQuestion(text.take(50))
                    handler.post {
                        val intent = Intent(this@AccessibilitySearchService, SearchResultActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            putExtra("question", question?.content ?: text)
                            putExtra("answer", question?.answer ?: "未在题库中找到匹配题目")
                            putExtra("analysis", question?.analysis ?: "")
                        }
                        startActivity(intent)
                    }
                } else {
                    handler.post {
                        Toast.makeText(this@AccessibilitySearchService, "未识别到文字", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "OCR failed", e)
                handler.post {
                    Toast.makeText(this@AccessibilitySearchService, "识别失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                unregisterReceiver(captureReceiver)
            } catch (e: Exception) {
                Log.e(TAG, "Unregister failed", e)
            }
        }
        ocrManager.close()
    }
}