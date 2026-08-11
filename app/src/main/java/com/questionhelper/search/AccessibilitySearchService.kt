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
import com.questionhelper.R
import com.questionhelper.data.QuestionRepository
import com.questionhelper.ocr.OcrManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class AccessibilitySearchService : AccessibilityService() {
    private var ocrManager: OcrManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private val TAG = "AccessibilitySearch"
    private var receiverRegistered = false
    @Volatile
    private var isConnected = false

    private fun ensureOcrManager(): OcrManager? {
        if (ocrManager == null) {
            try {
                ocrManager = OcrManager(this)
                Log.d(TAG, "OcrManager initialized lazily, ready=${ocrManager?.isReady}")
            } catch (e: Throwable) {
                Log.e(TAG, "OcrManager init failed", e)
                handler.post {
                    Toast.makeText(this, getString(R.string.ocr_init_failed, e.message), Toast.LENGTH_LONG).show()
                }
            }
        }
        return ocrManager
    }

    private val captureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "com.questionhelper.ACCESSIBILITY_CAPTURE") {
                val rect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra("rect", Rect::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra("rect")
                }
                Log.d(TAG, "Capture request: $rect")
                rect?.let { handleCaptureRequest(it) }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isConnected = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val filter = IntentFilter("com.questionhelper.ACCESSIBILITY_CAPTURE")
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(captureReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    registerReceiver(captureReceiver, filter)
                }
                receiverRegistered = true
            } catch (e: Throwable) {
                Log.e(TAG, "Register receiver failed", e)
            }
        }

        Toast.makeText(this, R.string.accessibility_service_started, Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Accessibility service connected")

        if (FloatWindowService.checkPermission(this)) {
            try {
                val intent = Intent(this, FloatWindowService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                Toast.makeText(this, R.string.float_ball_showing, Toast.LENGTH_SHORT).show()
            } catch (e: Throwable) {
                Log.e(TAG, "Start float window failed", e)
                Toast.makeText(this, R.string.float_ball_start_failed, Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, R.string.permission_overlay_required, Toast.LENGTH_LONG).show()
        }
    }

    private fun handleCaptureRequest(rect: Rect) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Toast.makeText(this, R.string.accessibility_screenshot_requires_android_11, Toast.LENGTH_LONG).show()
            return
        }
        if (!isConnected) {
            Log.w(TAG, "Service not connected yet, retry in 500ms")
            handler.postDelayed({ handleCaptureRequest(rect) }, 500)
            return
        }
        captureWithAccessibility(rect)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun captureWithAccessibility(rect: Rect) {
        try {
            val executor = Executors.newSingleThreadExecutor()
            val callbackClass = Class.forName("android.accessibilityservice.AccessibilityService\$TakeScreenshotCallback")
            val paramTypes = arrayOf<Class<*>>(
                Int::class.javaPrimitiveType!!,
                java.util.concurrent.Executor::class.java,
                callbackClass
            )
            val method = AccessibilityService::class.java.getDeclaredMethod("takeScreenshot", *paramTypes)
            val proxy = java.lang.reflect.Proxy.newProxyInstance(
                callbackClass.classLoader,
                arrayOf(callbackClass)
            ) { _, proxyMethod, args ->
                if (proxyMethod.name == "onSuccess") {
                    val result = args?.getOrNull(0)
                    try {
                        val bitmap = result?.let {
                            try {
                                val getBitmap = it.javaClass.getMethod("getBitmap")
                                getBitmap.invoke(it) as? Bitmap
                            } catch (e: Throwable) {
                                Log.e(TAG, "getBitmap failed", e)
                                null
                            }
                        }
                        if (bitmap != null) {
                            processScreenshotBitmap(bitmap, rect)
                        } else {
                            Log.e(TAG, "Screenshot returned null bitmap")
                            handler.post {
                                Toast.makeText(
                                    this@AccessibilitySearchService,
                                    R.string.screenshot_failed_null,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    } catch (e: Throwable) {
                        Log.e(TAG, "Process screenshot result failed", e)
                        handler.post {
                            Toast.makeText(
                                this@AccessibilitySearchService,
                                R.string.screenshot_crop_failed,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } finally {
                        // Android 14+ 必须释放 ScreenshotResult，否则后续截图可能被系统拒绝
                        releaseScreenshotResult(result)
                        executor.shutdown()
                    }
                } else if (proxyMethod.name == "onFailure") {
                    val errorCode = args?.getOrNull(0)
                    executor.shutdown()
                    Log.e(TAG, "Screenshot onFailure: $errorCode")
                    handler.post {
                        Toast.makeText(
                            this@AccessibilitySearchService,
                            getString(R.string.screenshot_failed_error_code, errorCode),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                null
            }
            method.invoke(this, Display.DEFAULT_DISPLAY, executor, proxy)
        } catch (e: Throwable) {
            Log.e(TAG, "takeScreenshot failed", e)
            handler.post {
                Toast.makeText(
                    this@AccessibilitySearchService,
                    getString(R.string.screenshot_failed_restricted),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * 释放 ScreenshotResult，避免 Android 14+ 上资源泄漏导致后续截图失败。
     */
    private fun releaseScreenshotResult(result: Any?) {
        if (result == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val releaseMethod = result.javaClass.getMethod("release")
                releaseMethod.invoke(result)
            }
            // API 30-33 没有 release() 方法，依赖 GC 回收
        } catch (e: Throwable) {
            Log.w(TAG, "release screenshot result failed", e)
        }
    }

    private fun processScreenshotBitmap(bitmap: Bitmap, rect: Rect) {
        try {
            val safeRect = Rect(
                rect.left.coerceIn(0, bitmap.width),
                rect.top.coerceIn(0, bitmap.height),
                rect.right.coerceIn(0, bitmap.width),
                rect.bottom.coerceIn(0, bitmap.height)
            )
            if (safeRect.width() <= 0 || safeRect.height() <= 0) {
                bitmap.recycle()
                handler.post {
                    Toast.makeText(this, R.string.screenshot_invalid_area, Toast.LENGTH_SHORT).show()
                }
                return
            }

            val cropped = Bitmap.createBitmap(
                bitmap,
                safeRect.left,
                safeRect.top,
                safeRect.width(),
                safeRect.height()
            )
            bitmap.recycle()
            processBitmap(cropped)
        } catch (e: Throwable) {
            Log.e(TAG, "Crop screenshot failed", e)
            bitmap.recycle()
            handler.post {
                Toast.makeText(this, R.string.screenshot_crop_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun processBitmap(bitmap: Bitmap) {
        val manager = ensureOcrManager()
        if (manager == null) {
            handler.post { Toast.makeText(this, R.string.ocr_not_initialized, Toast.LENGTH_SHORT).show() }
            bitmap.recycle()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val text = manager.recognizeFromBitmap(bitmap)
                bitmap.recycle()

                if (text.isNotEmpty()) {
                    val repo = QuestionRepository(QuestionApp.database.questionDao())
                    val question = repo.searchQuestion(text.take(50))
                    handler.post {
                        startActivity(Intent(this@AccessibilitySearchService, SearchResultActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            putExtra("question", question?.content ?: text)
                            putExtra("answer", question?.answer ?: getString(R.string.no_match_found))
                            putExtra("analysis", question?.analysis ?: "")
                        })
                    }
                } else {
                    handler.post { Toast.makeText(this@AccessibilitySearchService, R.string.ocr_no_text, Toast.LENGTH_SHORT).show() }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "OCR failed", e)
                handler.post { Toast.makeText(this@AccessibilitySearchService, R.string.ocr_failed, Toast.LENGTH_SHORT).show() }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "CAPTURE") {
            val rect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra("rect", Rect::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra("rect")
            }
            Log.d(TAG, "CAPTURE intent received, rect=$rect, connected=$isConnected")
            rect?.let { handleCaptureRequest(it) }
        }
        return START_STICKY
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        isConnected = false
        if (receiverRegistered && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try { unregisterReceiver(captureReceiver) } catch (_: Throwable) {}
        }
        ocrManager?.close()
        ocrManager = null
    }
}
