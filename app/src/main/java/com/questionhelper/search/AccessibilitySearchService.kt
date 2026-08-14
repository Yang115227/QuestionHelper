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
            showError("系统版本过低", "无障碍截图需要 Android 11 及以上系统")
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
                arrayOf<Class<*>>(callbackClass)
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
                                showError("截图失败", "无法从系统截图结果中提取图像，请尝试使用录屏截图方式")
                            }
                        } catch (e: Throwable) {
                            Log.e(TAG, "Process screenshot result failed", e)
                            showError("截图处理失败", "错误：${e.message}")
                        } finally {
                            releaseScreenshotResult(result)
                        }
                    }
                    "onFailure" -> {
                        val errorCode = args?.getOrNull(0)
                        executor.shutdown()
                        Log.e(TAG, "Screenshot onFailure: $errorCode")
                        showError("系统截图失败", "错误码：$errorCode\n无障碍截图可能被系统限制，请尝试录屏截图方式")
                    }
                }
                null
            }
            method.invoke(this, Display.DEFAULT_DISPLAY, executor, proxy)
        } catch (e: Throwable) {
            Log.e(TAG, "takeScreenshot failed", e)
            showError("无障碍截图不可用", "错误：${e.message}\n请尝试使用录屏截图方式")
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
                showError("框选区域无效", "所选区域超出屏幕范围或面积为零")
                return
            }

            val cropped = Bitmap.createBitmap(
                bitmap,
                safeRect.left,
                safeRect.top,
                safeRect.width(),
                safeRect.height()
            )
            val safeCropped = cropped.copy(Bitmap.Config.ARGB_8888, true)
            bitmap.recycle()
            cropped.recycle()

            Log.d(TAG, "Accessibility cropped: ${safeCropped.width}x${safeCropped.height}")
            processBitmap(safeCropped)
        } catch (e: Throwable) {
            Log.e(TAG, "Crop screenshot failed", e)
            bitmap.recycle()
            showError("截图裁剪失败", "错误：${e.message}")
        }
    }

    private fun processBitmap(bitmap: Bitmap) {
        val manager = QuestionApp.ocrManager

        if (!manager.isReady) {
            val error = manager.initError ?: "未知错误"
            Log.e(TAG, "OCR 未就绪: $error")
            showError("OCR 未初始化", "错误：$error\n请检查模型文件是否完整")
            bitmap.recycle()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "OCR starting, bitmap=${bitmap.width}x${bitmap.height}")
                val text = manager.recognizeFromBitmap(bitmap)
                Log.d(TAG, "OCR result: '$text'")
                bitmap.recycle()

                if (text.isNotEmpty()) {
                    val repo = QuestionRepository(QuestionApp.database.questionDao())
                    val question = repo.searchQuestion(text.take(50))

                    val questionText = question?.content ?: text
                    val answerText = question?.answer ?: "未在题库中找到匹配题目"
                    val analysisText = question?.analysis ?: ""
                    val isMatched = question != null

                    Log.d(TAG, "Search result: matched=$isMatched")

                    sendBroadcast(Intent(FloatWindowService.ACTION_SHOW_RESULT).apply {
                        putExtra(FloatWindowService.EXTRA_QUESTION, questionText)
                        putExtra(FloatWindowService.EXTRA_ANSWER, answerText)
                        putExtra(FloatWindowService.EXTRA_ANALYSIS, analysisText)
                        putExtra(FloatWindowService.EXTRA_MATCHED, isMatched)
                    })
                } else {
                    showError("未识别到文字", "OCR 未能识别出文字，请确保：\n1. 框选区域包含清晰的文字\n2. 文字与背景对比度足够\n3. 图片没有过度模糊")
                }
            } catch (e: Throwable) {
                Log.e(TAG, "OCR failed", e)
                showError("文字识别失败", "错误：${e.message}")
            }
        }
    }

    /**
     * 关键修复：直接显示错误悬浮窗（不依赖 Broadcast，避免被系统拦截）
     */
    private fun showError(title: String, message: String) {
        Log.e(TAG, "Error: $title - $message")

        // 方案1：尝试发送广播
        try {
            sendBroadcast(Intent(FloatWindowService.ACTION_SHOW_RESULT).apply {
                putExtra(FloatWindowService.EXTRA_QUESTION, "⚠️ $title")
                putExtra(FloatWindowService.EXTRA_ANSWER, message)
                putExtra(FloatWindowService.EXTRA_ANALYSIS, "点击悬浮球重新尝试框选")
                putExtra(FloatWindowService.EXTRA_MATCHED, false)
            })
        } catch (_: Exception) {}

        // 方案2：Service 自己直接显示临时悬浮窗（更可靠）
        handler.post {
            try {
                val wm = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
                val layout = android.widget.LinearLayout(this@AccessibilitySearchService).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    setPadding(60, 60, 60, 60)
                    background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = 32f
                        setColor(0xE6000000.toInt())
                    }
                    addView(android.widget.TextView(this@AccessibilitySearchService).apply {
                        this.text = "⚠️ $title"
                        textSize = 18f
                        setTextColor(0xFFFF5252.toInt())
                        setPadding(0, 0, 0, 16)
                    })
                    addView(android.widget.TextView(this@AccessibilitySearchService).apply {
                        this.text = message
                        textSize = 15f
                        setTextColor(0xFFFFFFFF.toInt())
                    })
                }
                val params = android.view.WindowManager.LayoutParams(
                    800,
                    android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    else
                        android.view.WindowManager.LayoutParams.TYPE_PHONE,
                    android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    android.graphics.PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = android.view.Gravity.CENTER
                }
                wm.addView(layout, params)
                handler.postDelayed({
                    try { wm.removeView(layout) } catch (_: Exception) {}
                }, 6000)
            } catch (_: Exception) {}
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
