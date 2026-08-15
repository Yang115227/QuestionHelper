package com.questionhelper.search

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.questionhelper.QuestionApp
import com.questionhelper.R
import com.questionhelper.data.QuestionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScreenCaptureService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())
    private val tag = "ScreenCaptureService"

    private var screenWidth: Int = 1080
    private var screenHeight: Int = 1920
    private var screenDensity: Int = 320
    private var foregroundStarted = false

    companion object {
        const val ACTION_CAPTURE = "com.questionhelper.action.CAPTURE"
        const val ACTION_PROJECTION_STOPPED = "com.questionhelper.action.PROJECTION_STOPPED"
        const val ACTION_PROJECTION_READY = "com.questionhelper.action.PROJECTION_READY"
        const val EXTRA_ERROR = "error"

        @Volatile
        var isRunning = false
            private set

        @Volatile
        var isInitialized = false
            private set

        @Volatile
        var isInitializing = false
            private set

        @Volatile
        var lastError: String? = null
            private set

        fun markProjectionStopped() {
            isInitialized = false
            isInitializing = false
        }

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                putExtra("result_code", resultCode)
                putExtra("result_data", data)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ScreenCaptureService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        readScreenMetrics()
    }

    private fun readScreenMetrics() {
        try {
            val metrics = DisplayMetrics()
            val wm = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
            screenDensity = metrics.densityDpi
            Log.d(tag, "Screen metrics: ${screenWidth}x$screenHeight, density=$screenDensity")
        } catch (e: Throwable) {
            Log.e(tag, "Read screen metrics failed", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        ensureForeground()

        when (intent.action) {
            ACTION_CAPTURE -> {
                val rect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra("rect", Rect::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra("rect")
                }
                if (rect != null && isInitialized) {
                    captureAndSearch(rect)
                } else if (!isInitialized) {
                    showError("截图服务未就绪", "录屏权限可能已被系统回收，请重新点击「悬浮搜题」授权")
                } else {
                    showError("框选区域无效", "请重新框选题目区域")
                }
                return START_STICKY
            }
        }

        val resultCode = intent.getIntExtra("result_code", android.app.Activity.RESULT_CANCELED)
        val data = intent.getParcelableExtra<Intent>("result_data")

        if (resultCode != android.app.Activity.RESULT_OK || data == null) {
            reportInitFailed(getString(R.string.screen_capture_invalid_data))
            stopSelf()
            return START_NOT_STICKY
        }

        releaseProjection()
        isInitializing = true
        lastError = null

        if (!initializeProjection(resultCode, data)) {
            val error = lastError ?: getString(R.string.screen_capture_init_failed)
            reportInitFailed(error)
            stopSelf()
            return START_NOT_STICKY
        }

        isRunning = true
        isInitialized = true
        isInitializing = false
        lastError = null
        sendBroadcast(Intent(ACTION_PROJECTION_READY).apply { setPackage(packageName) })
        return START_STICKY
    }

    private fun ensureForeground() {
        if (!foregroundStarted) {
            startForeground()
            foregroundStarted = true
        }
    }

    private fun reportInitFailed(error: String) {
        isInitializing = false
        lastError = error
        handler.post {
            Toast.makeText(this, getString(R.string.screen_capture_init_failed_with_reason, error), Toast.LENGTH_LONG).show()
        }
        sendBroadcast(Intent(ACTION_PROJECTION_STOPPED).apply {
            setPackage(packageName)
            putExtra(EXTRA_ERROR, error)
        })
    }

    private fun initializeProjection(resultCode: Int, data: Intent): Boolean {
        return try {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = manager.getMediaProjection(resultCode, data)

            if (mediaProjection == null) {
                lastError = getString(R.string.screen_capture_projection_null)
                return false
            }

            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    isInitialized = false
                    isInitializing = false
                    releaseProjection()
                    sendBroadcast(Intent(ACTION_PROJECTION_STOPPED).apply { setPackage(packageName) })
                }
            }, handler)

            imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                screenWidth, screenHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, handler
            )
            virtualDisplay != null
        } catch (e: Exception) {
            lastError = getString(R.string.screen_capture_init_exception, e.message)
            false
        }
    }

    private fun captureAndSearch(rect: Rect) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bitmap = captureScreen()
                if (bitmap == null) {
                    showError("截图失败", "无法获取屏幕图像，请检查录屏权限是否仍在生效")
                    return@launch
                }

                val safeRect = Rect(
                    rect.left.coerceIn(0, bitmap.width),
                    rect.top.coerceIn(0, bitmap.height),
                    rect.right.coerceIn(0, bitmap.width),
                    rect.bottom.coerceIn(0, bitmap.height)
                )
                if (safeRect.width() <= 0 || safeRect.height() <= 0) {
                    bitmap.recycle()
                    showError("框选区域无效", "所选区域超出屏幕范围或面积为零")
                    return@launch
                }

                val cropped = Bitmap.createBitmap(bitmap, safeRect.left, safeRect.top, safeRect.width(), safeRect.height())
                val safeCropped = cropped.copy(Bitmap.Config.ARGB_8888, true)
                bitmap.recycle()
                cropped.recycle()
                processBitmap(safeCropped)
            } catch (e: Throwable) {
                showError("截图处理异常", "错误：${e.message}")
            }
        }
    }

    private fun captureScreen(): Bitmap? {
        if (!isInitialized || imageReader == null) return null
        Thread.sleep(300)
        repeat(10) { attempt ->
            var image: Image? = null
            try {
                image = imageReader?.acquireLatestImage()
                if (image == null) image = imageReader?.acquireNextImage()
                if (image != null) {
                    val bitmap = imageToBitmap(image)
                    if (bitmap != null && !isBlankBitmap(bitmap)) return bitmap
                    bitmap?.recycle()
                }
            } catch (e: Exception) {
                Log.e(tag, "Capture attempt $attempt failed", e)
            } finally {
                image?.close()
            }
            Thread.sleep(500)
        }
        return null
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        return try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val width = image.width
            val height = image.height
            buffer.rewind()
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val rowBuffer = ByteArray(rowStride)
            val pixels = IntArray(width * height)
            for (row in 0 until height) {
                buffer.get(rowBuffer, 0, rowStride)
                for (col in 0 until width) {
                    val idx = col * pixelStride
                    val r = rowBuffer[idx].toInt() and 0xFF
                    val g = rowBuffer[idx + 1].toInt() and 0xFF
                    val b = rowBuffer[idx + 2].toInt() and 0xFF
                    val a = rowBuffer[idx + 3].toInt() and 0xFF
                    pixels[row * width + col] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap
        } catch (e: Throwable) {
            null
        }
    }

    private fun isBlankBitmap(bitmap: Bitmap): Boolean {
        // 简单的空白检测
        val sampleStep = maxOf(1, minOf(bitmap.width, bitmap.height) / 10)
        var nonBlank = false
        for (y in 0 until bitmap.height step sampleStep) {
            for (x in 0 until bitmap.width step sampleStep) {
                val pixel = bitmap.getPixel(x, y)
                if ((pixel and 0xFFFFFF) > 0x101010 && (pixel ushr 24) > 10) {
                    nonBlank = true
                    break
                }
            }
            if (nonBlank) break
        }
        return !nonBlank
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

                    val questionText = question?.content ?: text
                    val answerText = question?.answer ?: "未在题库中找到匹配题目"
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

    private fun releaseProjection() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null
        isInitialized = false
        isInitializing = false
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseProjection()
        isRunning = false
        isInitializing = false
        foregroundStarted = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForeground() {
        val channelId = "screen_capture"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, getString(R.string.screen_capture_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.screen_capture_service_running))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
        startForeground(1, notification)
    }
}