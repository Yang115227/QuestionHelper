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
                                showError("截图失败", "无法从系统截图结果中提取图像")
                            }
                        } catch (e: Throwable) {
                            showError("截图处理失败", "错误：${e.message}")
                        } finally {
                            releaseScreenshotResult(result)
                        }
                    }
                    "onFailure" -> {
                        val errorCode = args?.getOrNull(0)
                        executor.shutdown()
                        showError("系统截图失败", "错误码：$errorCode")
                    }
                }
                null
            }
            method.invoke(this, Display.DEFAULT_DISPLAY, executor, proxy)
        } catch (e: Throwable) {
            showError("无障碍截图不可用", "错误：${e.message}")
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
        try {
            val method = clazz.getMethod("getBitmap")
            method.invoke(result)?.let { if (it is Bitmap) return it }
        } catch (_: Throwable) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                val method = clazz.getMethod("getHardwareBuffer")
                val hardwareBuffer = method.invoke(result)
                if (hardwareBuffer is HardwareBuffer) {
                    val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, null)
                    if (bitmap != null) return bitmap
                }
            } catch (_: Throwable) {}
        }

        try {
            var currentClass: Class<*>? = clazz
            while (currentClass != null && currentClass != Any::class.java) {
                for (method in (currentClass.methods + currentClass.declaredMethods).distinct()) {
                    if (method.parameterTypes.isEmpty()) {
                        method.isAccessible = true
                        if (method.returnType == Bitmap::class.java) {
                            method.invoke(result)?.let { if (it is Bitmap) return it }
                        }
                    }
                }
                currentClass = currentClass.superclass
            }
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

            val cropped = Bitmap.createBitmap(bitmap, safeRect.left, safeRect.top, safeRect.width(), safeRect.height())
            val safeCropped = cropped.copy(Bitmap.Config.ARGB_8888, true)
            bitmap.recycle()
            cropped.recycle()
            processBitmap(safeCropped)
        } catch (e: Throwable) {
            bitmap.recycle()
            showError("截图裁剪失败", "错误：${e.message}")
        }
    }

    private fun processBitmap(bitmap: Bitmap) {
        val manager = QuestionApp.ocrManager
        if (!manager.isReady) {
            showError("OCR 未初始化", manager.initError ?: "未知错误")
            bitmap.recycle()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val text = manager.recognizeFromBitmap(bitmap)
                bitmap.recycle()
                if (text.isNotEmpty()) {
                    val repo = QuestionRepository(QuestionApp.database.questionDao())
                    val question = repo.searchQuestionSmart(text)

                    val questionText = if (question != null) extractQuestionStem(question.content) else text

                    val answerText = if (question != null) {
                        formatAnswerWithOptions(question.content, question.answer)
                    } else {
                        "未在题库中找到匹配题目"
                    }

                    val analysisText = question?.analysis ?: ""
                    val isMatched = question != null

                    sendBroadcast(Intent(FloatWindowService.ACTION_SHOW_RESULT).apply {
                        setPackage(packageName)
                        putExtra(FloatWindowService.EXTRA_QUESTION, questionText)
                        putExtra(FloatWindowService.EXTRA_ANSWER, answerText)
                        putExtra(FloatWindowService.EXTRA_ANALYSIS, analysisText)
                        putExtra(FloatWindowService.EXTRA_MATCHED, isMatched)
                    })
                } else {
                    showError("未识别到文字", "请确保框选区域包含清晰的文字")
                }
            } catch (e: Throwable) {
                showError("文字识别失败", "错误：${e.message}")
            }
        }
    }

    private fun extractQuestionStem(content: String): String {
        val optionPattern = Regex("^\\s*[A-Da-d]\\s*[.、．:：）)]")
        val stemLines = mutableListOf<String>()
        for (line in content.lines()) {
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !optionPattern.containsMatchIn(trimmed)) {
                stemLines.add(trimmed)
            }
        }
        return if (stemLines.isEmpty()) content.trim() else stemLines.joinToString("\n")
    }

    private fun extractOptions(content: String): List<String> {
        val options = mutableListOf<String>()
        val pattern = Regex("([A-Da-d])\\s*[.、．:：）)]\\s*(.*?)(?=\\s*[A-Da-d]\\s*[.、．:：）)]|\\n|$)", RegexOption.DOT_MATCHES_ALL)
        pattern.findAll(content).forEach { match ->
            val letter = match.groupValues[1].uppercase()
            val text = match.groupValues[2].trim()
            if (text.isNotEmpty()) {
                options.add("$letter. $text")
            }
        }
        if (options.isEmpty()) {
            content.lines().forEach { line ->
                val lineMatch = Regex("^([A-Da-d])\\s*[.、．:：）)]\\s*(.+)").find(line.trim())
                if (lineMatch != null) {
                    val letter = lineMatch.groupValues[1].uppercase()
                    val text = lineMatch.groupValues[2].trim()
                    options.add("$letter. $text")
                }
            }
        }
        return options
    }

    private fun formatAnswerWithOptions(content: String, answer: String): String {
        val options = extractOptions(content)
        if (options.isEmpty()) return answer

        val correctLetter = answer.trim().firstOrNull()?.uppercaseChar() ?: return answer

        val sb = StringBuilder()
        for (opt in options) {
            val letter = opt.firstOrNull()?.uppercaseChar()
            val cleanedOpt = opt.replaceFirst(Regex("^([A-Z])\\s*[.、．]\\s*"), "$1 ")
            if (letter == correctLetter) {
                sb.append("【正确】").append(cleanedOpt)
            } else {
                sb.append(cleanedOpt)
            }
            sb.append('\n')
        }
        return sb.toString().trimEnd()
    }

    private fun showError(title: String, message: String) {
        sendBroadcast(Intent(FloatWindowService.ACTION_SHOW_RESULT).apply {
            setPackage(packageName)
            putExtra(FloatWindowService.EXTRA_QUESTION, "⚠️ $title")
            putExtra(FloatWindowService.EXTRA_ANSWER, message)
            putExtra(FloatWindowService.EXTRA_ANALYSIS, "点击悬浮球重新尝试框选")
            putExtra(FloatWindowService.EXTRA_MATCHED, false)
        })
        handler.post { Toast.makeText(this, "$title: $message", Toast.LENGTH_LONG).show() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "CAPTURE") {
            val rect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra("rect", Rect::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra("rect")
            }
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