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
        startForeground()
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
                        // ✅ 修复：R.string.screenshot.screenshot_service_not_ready → R.string.screenshot_service_not_ready
                        Toast.makeText(this, R.string.screenshot_service_not_ready, Toast.LENGTH_SHORT).show()
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
                val bitmap = captureScreen() ?: run {
                    handler.post {
                        Toast.makeText(this@ScreenCaptureService, R.string.screenshot_failed_null, Toast.LENGTH_SHORT).show()
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
                bitmap.recycle()

                processBitmap(cropped)
            } catch (e: Throwable) {
                Log.e(tag, "Capture and search failed", e)
                handler.post {
                    Toast.makeText(this@ScreenCaptureService, getString(R.string.screenshot_failed_with_reason, e.message), Toast.LENGTH_SHORT).show()
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
                    val bitmap = imageToBitmap(image)
                    if (bitmap != null && !isBlankBitmap(bitmap)) {
                        return bitmap
                    }
                    bitmap?.recycle()
                    Log.w(tag, "Capture attempt $attempt got blank bitmap")
                } else {
                    Log.w(tag, "Capture attempt $attempt got null image")
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
        return null
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        return try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width

            val widthWithPadding = image.width + rowPadding / pixelStride
            val bitmap = Bitmap.createBitmap(
                widthWithPadding,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            if (bitmap.width > image.width) {
                Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height).also {
                    bitmap.recycle()
                }
            } else {
                bitmap
            }
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
                    if (pixel and 0xFFFFFF != 0 && pixel ushr 24 > 10) {
                        hasNonBlank = true
                        break@outer
                    }
                }
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
            Log.w(tag, "OCR not ready, maybe paddle models missing")
            handler.post { Toast.makeText(this, R.string.ocr_not_initialized, Toast.LENGTH_LONG).show() }
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
                        startActivity(Intent(this@ScreenCaptureService, SearchResultActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            putExtra("question", question?.content ?: text)
                            putExtra("answer", question?.answer ?: getString(R.string.no_match_found))
                            putExtra("analysis", question?.analysis ?: "")
                        })
                    }
                } else {
                    handler.post { Toast.makeText(this@ScreenCaptureService, R.string.ocr_no_text, Toast.LENGTH_SHORT).show() }
                }
            } catch (e: Throwable) {
                Log.e(tag, "OCR failed", e)
                handler.post { Toast.makeText(this@ScreenCaptureService, R.string.ocr_failed, Toast.LENGTH_SHORT).show() }
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
