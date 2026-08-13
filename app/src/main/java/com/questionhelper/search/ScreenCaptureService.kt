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
            Log.w(tag, "Service restarted by system without intent, stopping self")
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
                Log.d(tag, "CAPTURE action received, rect=$rect, initialized=$isInitialized")
                if (rect != null && isInitialized) {
                    captureAndSearch(rect)
                } else if (!isInitialized) {
                    handler.post {
                        Toast.makeText(this, "截图服务未就绪，请重新授权录屏", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e(tag, "CAPTURE received but rect is null")
                    handler.post {
                        Toast.makeText(this, "截图区域为空，请重新框选", Toast.LENGTH_SHORT).show()
                    }
                }
                return START_STICKY
            }
        }

        val resultCode = intent.getIntExtra("result_code", android.app.Activity.RESULT_CANCELED)
        val data = intent.getParcelableExtra<Intent>("result_data")

        if (resultCode != android.app.Activity.RESULT_OK || data == null) {
            Log.e(tag, "Invalid MediaProjection data, resultCode=$resultCode")
            reportInitFailed(getString(R.string.screen_capture_invalid_data))
            stopSelf()
            return START_NOT_STICKY
        }

        releaseProjection()
        isInitializing = true
        lastError = null

        if (!initializeProjection(resultCode, data)) {
            val error = lastError ?: getString(R.string.screen_capture_init_failed)
            Log.e(tag, "MediaProjection initialization failed: $error")
            reportInitFailed(error)
            stopSelf()
            return START_NOT_STICKY
        }

        isRunning = true
        isInitialized = true
        isInitializing = false
        lastError = null
        sendBroadcast(Intent(ACTION_PROJECTION_READY))
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
            putExtra(EXTRA_ERROR, error)
        })
    }

    private fun initializeProjection(resultCode: Int, data: Intent): Boolean {
        return try {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = manager.getMediaProjection(resultCode, data)

            if (mediaProjection == null) {
                Log.e(tag, "getMediaProjection returned null")
                lastError = getString(R.string.screen_capture_projection_null)
                return false
            }

            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.w(tag, "MediaProjection stopped by system")
                    isInitialized = false
                    isInitializing = false
                    releaseProjection()
                    sendBroadcast(Intent(ACTION_PROJECTION_STOPPED))
                }
            }, handler)

            imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
            if (imageReader == null) {
                lastError = getString(R.string.screen_capture_image_reader_null)
                return false
            }
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                screenWidth, screenHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, handler
            )
            if (virtualDisplay == null) {
                lastError = getString(R.string.screen_capture_virtual_display_null)
                return false
            }

            true
        } catch (e: Exception) {
            Log.e(tag, "Initialize projection failed", e)
            lastError = getString(R.string.screen_capture_init_exception, e.message)
            false
        }
    }

    private fun captureAndSearch(rect: Rect) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(tag, "captureAndSearch started, rect=$rect")
                val bitmap = captureScreen()
                if (bitmap == null) {
                    Log.w(tag, "captureScreen returned null")
                    handler.post {
                        Toast.makeText(this@ScreenCaptureService, "截图失败：无法获取屏幕图像，请检查录屏权限或重试", Toast.LENGTH_LONG).show()
                    }
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
                    handler.post {
                        Toast.makeText(this@ScreenCaptureService, R.string.screenshot_invalid_area, Toast.LENGTH_SHORT).show()
                    }
                    return@launch
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

                Log.d(tag, "Cropped bitmap: ${safeCropped.width}x${safeCropped.height}")
                processBitmap(safeCropped)
            } catch (e: Throwable) {
                Log.e(tag, "Capture and search failed", e)
                handler.post {
                    Toast.makeText(this@ScreenCaptureService, "截图处理失败：${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun captureScreen(): Bitmap? {
        if (!isInitialized || imageReader == null) {
            Log.w(tag, "Capture called but not initialized")
            return null
        }
        repeat(5) { attempt ->
            var image: Image? = null
            try {
                image = imageReader?.acquireLatestImage()
                if (image != null) {
                    Log.d(tag, "Capture attempt $attempt: image acquired ${image.width}x${image.height}")
                    val bitmap = imageToBitmap(image)
                    if (bitmap != null) {
                        if (!isBlankBitmap(bitmap)) {
                            Log.d(tag, "Capture attempt $attempt: valid bitmap obtained")
                            return bitmap
                        }
                        Log.w(tag, "Capture attempt $attempt: blank bitmap, retrying...")
                        bitmap.recycle()
                    } else {
                        Log.w(tag, "Capture attempt $attempt: imageToBitmap returned null")
                    }
                } else {
                    Log.w(tag, "Capture attempt $attempt: null image")
                }
            } catch (e: Exception) {
                Log.e(tag, "Capture attempt $attempt failed", e)
            } finally {
                try {
                    image?.close()
                } catch (_: Throwable) {}
            }
            Thread.sleep(200)
        }
        Log.e(tag, "All capture attempts failed")
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

            Log.d(tag, "imageToBitmap: width=$width, height=$height, pixelStride=$pixelStride, rowStride=$rowStride")

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
            Log.e(tag, "Image to bitmap failed", e)
            null
        }
    }

    private fun isBlankBitmap(bitmap: Bitmap): Boolean {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            if (width <= 0 || height <= 0) return true
            val sampleStep = maxOf(1, minOf(width, height) / 10)
            var hasNonBlank = false
            outer@ for (y in 0 until height step sampleStep) {
                for (x in 0 until width step sampleStep) {
                    val pixel = bitmap.getPixel(x, y)
                    val rgb = pixel and 0xFFFFFF
                    val alpha = pixel ushr 24
                    if (rgb > 0x101010 && alpha > 10) {
                        hasNonBlank = true
                        break@outer
                    }
                }
            }
            if (!hasNonBlank) {
                Log.w(tag, "Bitmap appears blank (all dark/transparent)")
            }
            !hasNonBlank
        } catch (e: Throwable) {
            Log.e(tag, "Check blank bitmap failed", e)
            false
        }
    }

    private fun processBitmap(bitmap: Bitmap) {
        val manager = QuestionApp.ocrManager

        if (!manager.isReady) {
            val error = manager.initError ?: "未知错误"
            Log.e(tag, "OCR 未就绪: $error")
            handler.post {
                Toast.makeText(this, "OCR 未初始化\n$error", Toast.LENGTH_LONG).show()
            }
            bitmap.recycle()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(tag, "OCR starting, bitmap=${bitmap.width}x${bitmap.height}")
                val text = manager.recognizeFromBitmap(bitmap)
                Log.d(tag, "OCR result: '$text'")
                bitmap.recycle()

                if (text.isNotEmpty()) {
                    val repo = QuestionRepository(QuestionApp.database.questionDao())
                    val question = repo.searchQuestion(text.take(50))

                    val questionText = question?.content ?: text
                    val answerText = question?.answer ?: "未在题库中找到匹配题目"
                    val analysisText = question?.analysis ?: ""
                    val isMatched = question != null

                    Log.d(tag, "Search result: matched=$isMatched, question=${questionText.take(20)}")

                    sendBroadcast(Intent(FloatWindowService.ACTION_SHOW_RESULT).apply {
                        putExtra(FloatWindowService.EXTRA_QUESTION, questionText)
                        putExtra(FloatWindowService.EXTRA_ANSWER, answerText)
                        putExtra(FloatWindowService.EXTRA_ANALYSIS, analysisText)
                        putExtra(FloatWindowService.EXTRA_MATCHED, isMatched)
                    })
                } else {
                    handler.post {
                        Toast.makeText(this@ScreenCaptureService, "未识别到文字，请确保框选区域包含清晰的文字", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Throwable) {
                Log.e(tag, "OCR failed", e)
                handler.post {
                    Toast.makeText(this@ScreenCaptureService, "文字识别失败：${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun releaseProjection() {
        try {
            virtualDisplay?.release()
        } catch (e: Exception) {
            Log.e(tag, "Release virtualDisplay failed", e)
        }
        virtualDisplay = null

        try {
            imageReader?.close()
        } catch (e: Exception) {
            Log.e(tag, "Close imageReader failed", e)
        }
        imageReader = null

        try {
            mediaProjection?.stop()
        } catch (e: Exception) {
            Log.e(tag, "Stop mediaProjection failed", e)
        }
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
