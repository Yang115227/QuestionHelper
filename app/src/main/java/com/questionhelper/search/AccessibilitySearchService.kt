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
import java.util.concurrent.Executors
import android.util.Log
import android.view.Display
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
    private var receiverRegistered = false

    private val captureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "com.questionhelper.ACCESSIBILITY_CAPTURE") {
                val rect = intent.getParcelableExtra<Rect>("rect")
                Log.d(TAG, "Capture request: $rect")
                rect?.let { captureWithAccessibility(it) }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        ocrManager = OcrManager(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val filter = IntentFilter("com.questionhelper.ACCESSIBILITY_CAPTURE")
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(captureReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    registerReceiver(captureReceiver, filter)
                }
                receiverRegistered = true
            } catch (e: Exception) {
                Log.e(TAG, "Register receiver failed", e)
            }
        }

        Toast.makeText(this, "无障碍搜题服务已启动", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Accessibility service connected")

        if (FloatWindowService.checkPermission(this)) {
            try {
                val intent = Intent(this, FloatWindowService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                Toast.makeText(this, "悬浮球已显示", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Start float window failed", e)
                Toast.makeText(this, "悬浮球启动失败，请手动返回App开启", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "请返回App开启悬浮窗权限", Toast.LENGTH_LONG).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun captureWithAccessibility(rect: Rect) {
        val executor = Executors.newSingleThreadExecutor()
        takeScreenshot(Display.DEFAULT_DISPLAY, executor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshotResult: AccessibilityService.ScreenshotResult?) {
                    val bitmap = screenshotResult?.let {
                        try {
                            val m = it.javaClass.getMethod("getBitmap")
                            m.invoke(it) as? Bitmap
                        } catch (_: Exception) { null }
                    }
                    if (bitmap != null) {
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
                    } else {
                        Log.e(TAG, "Screenshot returned null")
                        handler.post { Toast.makeText(this@AccessibilitySearchService, "截图失败", Toast.LENGTH_SHORT).show() }
                    }
                }
            })
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
                        startActivity(Intent(this@AccessibilitySearchService, SearchResultActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            putExtra("question", question?.content ?: text)
                            putExtra("answer", question?.answer ?: "未在题库中找到匹配题目")
                            putExtra("analysis", question?.analysis ?: "")
                        })
                    }
                } else {
                    handler.post { Toast.makeText(this@AccessibilitySearchService, "未识别到文字", Toast.LENGTH_SHORT).show() }
                }
            } catch (e: Exception) {
                Log.e(TAG, "OCR failed", e)
                handler.post { Toast.makeText(this@AccessibilitySearchService, "识别失败", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        if (receiverRegistered && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try { unregisterReceiver(captureReceiver) } catch (_: Exception) {}
        }
        if (::ocrManager.isInitialized) ocrManager.close()
    }
}