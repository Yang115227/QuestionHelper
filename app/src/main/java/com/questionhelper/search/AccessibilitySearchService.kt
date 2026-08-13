package com.questionhelper.search

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Rect
import android.hardware.HardwareBuffer
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class AccessibilitySearchService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val TAG = "AccessibilitySearch"
    private var receiverRegistered = false
    @Volatile
    private var isConnected = false

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
                when (proxyMethod.name) {
                    "onSuccess" -> {
                        val result = args?.getOrNull(0)
                        executor.shutdown()
                        try {
                            val bitmap = extractBitmapRobust(result)
                            if (bitmap != null) {
                                processScreenshotBitmap(bitmap, rect)
                            } else {
                                Log.e(TAG, "Screenshot bitmap is null")
                                handler.post {
                                    Toast.makeText(
                                        this@AccessibilitySearchService,
                                        getString(R.string.screenshot_reflection_failed, "Bitmap extraction returned null"),
                                        Toast.LENGTH_LONG
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
                            releaseScreenshotResult(result)
                        }
                    }
                    "onFailure" -> {
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

    private fun releaseScreenshotResult(result: Any?) {
        if (result == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val releaseMethod = result.javaClass.getMethod("release")
                releaseMethod.invoke(result)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "release screenshot result failed", e)
        }
    }

    private fun extractBitmapRobust(result: Any?): Bitmap? {
        if (result == null) return null
        if (result is Bitmap) return result

        val clazz = result.javaClass
        Log.d(TAG, "Screenshot result class: ${clazz.name}")

        try {
            val method = clazz.getMethod("getBitmap")
            method.invoke(result)?.let {
                if (it is Bitmap) return it
            }
        } catch (_: Throwable) {}

        try {
            val method = clazz.getDeclaredMethod("getBitmap")
            method.isAccessible = true
            method.invoke(result)?.let {
                if (it is Bitmap) return it
            }
        } catch (_: Throwable) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                val method = clazz.getMethod("getHardwareBuffer")
                val hardwareBuffer = method.invoke(result)
                if (hardwareBuffer is HardwareBuffer) {
                    val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, null)
                    if (bitmap != null) {
                        Log.d(TAG, "Bitmap extracted from HardwareBuffer")
                        return bitmap
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "getHardwareBuffer failed", e)
            }
        }

        try {
            var currentClass: Class<*>? = clazz
            while (currentClass != null && currentClass != Any::class.java) {
                val allMethods = (currentClass.methods + currentClass.declaredMethods).distinct()
                for (method in allMethods) {
                    if (method.parameterTypes.isEmpty()) {
                        method.isAccessible = true
                        when {
                            method.returnType == Bitmap::class.java -> {
                                method.invoke(result)?.let {
                                    if (it is Bitmap) return it
                                }
                            }
                            method.returnType == HardwareBuffer::class.java && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                                method.invoke(result)?.let { hb ->
                                    if (hb is HardwareBuffer) {
                                        Bitmap.wrapHardwareBuffer(hb, null)?.let { return it }
                                    }
                                }
                            }
                        }
                    }
                }
                currentClass = currentClass.superclass
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Scan methods failed", e)
        }

        try {
            var currentClass: Class<*>? = clazz
            while (currentClass != null && currentClass != Any::class.java) {
                val allFields = (currentClass.fields + currentClass.declaredFields).distinct()
                for (field in allFields) {
                    field.isAccessible = true
                    when {
                        field.type == Bitmap::class.java -> {
                            field.get(result)?.let {
                                if (it is Bitmap) return it
                            }
                        }
                        field.type == HardwareBuffer::class.java && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                            field.get(result)?.let { hb ->
                                if (hb is HardwareBuffer) {
                                    Bitmap.wrapHardwareBuffer(hb, null)?.let { return it }
                                }
                            }
                        }
                    }
                }
                currentClass = currentClass.superclass
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Scan fields failed", e)
        }

        try {
            val methods = clazz.declaredMethods.map { "${it.name}:${it.returnType.simpleName}" }
            val fields = clazz.declaredFields.map { "${it.name}:${it.type.simpleName}" }
            Log.d(TAG, "Declared methods: $methods")
            Log.d(TAG, "Declared fields: $fields")
        } catch (_: Throwable) {}

        return null
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
        val manager = QuestionApp.ocrManager

        if (!manager.isReady) {
            val error = manager.initError ?: "未知错误"
            Log.e(TAG, "OCR 未就绪: $error")
            handler.post {
                Toast.makeText(this, "OCR 未初始化\n$error", Toast.LENGTH_LONG).show()
            }
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

                    val questionText = question?.content ?: text
                    val answerText = question?.answer ?: "未在题库中找到匹配题目"
                    val analysisText = question?.analysis ?: ""
                    val isMatched = question != null

                    // ✅ 改为发送广播显示悬浮结果窗
                    sendBroadcast(Intent(FloatWindowService.ACTION_SHOW_RESULT).apply {
                        putExtra(FloatWindowService.EXTRA_QUESTION, questionText)
                        putExtra(FloatWindowService.EXTRA_ANSWER, answerText)
                        putExtra(FloatWindowService.EXTRA_ANALYSIS, analysisText)
                        putExtra(FloatWindowService.EXTRA_MATCHED, isMatched)
                    })
                } else {
                    handler.post {
                        Toast.makeText(this@AccessibilitySearchService, R.string.ocr_no_text, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "OCR failed", e)
                handler.post {
                    Toast.makeText(this@AccessibilitySearchService, R.string.ocr_failed, Toast.LENGTH_SHORT).show()
                }
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
    }
}
