package com.questionhelper.search

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.questionhelper.QuestionApp
import com.questionhelper.data.QuestionRepository
import com.questionhelper.ocr.OcrManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScreenCaptureService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private lateinit var ocrManager: OcrManager
    private val handler = Handler(Looper.getMainLooper())
    private var metrics: DisplayMetrics? = null
    private var isInitialized = false
    private val imageLock = Any()
    private var cachedImage: Image? = null

    companion object {
        @Volatile
        var isRunning = false
        @Volatile
        var isInitialized = false
            private set

        private const val TAG = "ScreenCapture"
        private const val CHANNEL_ID = "screen_capture"
        private const val NOTIFICATION_ID = 1001

        fun requestPermission(activity: android.app.Activity) {
            val manager = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            activity.startActivityForResult(manager.createScreenCaptureIntent(), 1001)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        ocrManager = OcrManager(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        Log.d(TAG, "Service created")

        // 注册广播接收者
        registerReceiver(captureReceiver, IntentFilter("com.questionhelper.CAPTURE_SCREEN"), if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Context.RECEIVER_NOT_EXPORTED else 0)
    }

    private val captureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "com.questionhelper.CAPTURE_SCREEN") {
                val rect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra("rect", Rect::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra("rect") as? Rect
                }
                rect?.let { captureArea(it) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isInitialized) {
            if (intent == null) {
                isRunning = false
                stopSelf()
                return START_NOT_STICKY
            }

            val code = intent.getIntExtra("result_code", 0)
            val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra("result_data", Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra("result_data") as? Intent
            }

            if (code != 0 && data != null) {
                initMediaProjection(code, data)
            } else {
                Log.e(TAG, "Missing result_code or result_data")
                isRunning = false
                stopSelf()
                return START_NOT_STICKY
            }
        }

        if (intent?.action == "CAPTURE") {
            val rect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra("rect", Rect::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra("rect") as? Rect
            }
            rect?.let { captureArea(it) }
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "录屏搜题服务", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "保持录屏搜题服务运行" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("搜题助手")
            .setContentText("录屏搜题服务运行中")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setOngoing(true)
            .build()
    }

    private fun initMediaProjection(resultCode: Int, resultData: Intent) {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        try {
            mediaProjection = manager.getMediaProjection(resultCode, resultData)
            if (mediaProjection == null) {
                Log.e(TAG, "getMediaProjection returned null")
                Toast.makeText(this, "录屏初始化失败：无法获取投影", Toast.LENGTH_SHORT).show()
                return
            }

            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    super.onStop()
                    Log.d(TAG, "MediaProjection stopped")
                    stopSelf()
                }
            }, handler)

            setupImageReader()
            isInitialized = true
            ScreenCaptureService.isInitialized = true
            Log.d(TAG, "MediaProjection initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaProjection", e)
            Toast.makeText(this, "录屏初始化失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupImageReader() {
        metrics = DisplayMetrics()
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getRealMetrics(metrics)

        imageReader = ImageReader.newInstance(
            metrics!!.widthPixels, metrics!!.heightPixels, PixelFormat.RGBA_8888, 2
        )

        // 缓存帧：OnImageAvailableListener 持续缓存最新帧
        imageReader?.setOnImageAvailableListener({ reader ->
            synchronized(imageLock) {
                cachedImage?.close()
                cachedImage = reader.acquireLatestImage()
            }
        }, handler)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            metrics!!.widthPixels, metrics!!.heightPixels, metrics!!.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, handler
        )

        Log.d(TAG, "VirtualDisplay: ${metrics!!.widthPixels}x${metrics!!.heightPixels}")
    }

    private fun captureArea(rect: Rect) {
        Log.d(TAG, "captureArea called, isInitialized=$isInitialized, imageReader=${imageReader != null}")
        if (!isInitialized || imageReader == null) {
            handler.post { Toast.makeText(this, "录屏服务未就绪，请重新授权", Toast.LENGTH_SHORT).show() }
            return
        }
        
        handler.post { Toast.makeText(this, "正在截图...", Toast.LENGTH_SHORT).show() }

        handler.postDelayed({
            synchronized(imageLock) {
                val image = cachedImage
                cachedImage = null
                
                Log.d(TAG, "Cached image: ${image != null}")
                
                if (image == null) {
                    handler.post { Toast.makeText(this, "截图失败：无可用图像，请重试", Toast.LENGTH_LONG).show() }
                    return@postDelayed
                }

                try {
                    val bitmap = imageToBitmap(image)
                    image.close()

                    if (bitmap == null) {
                        handler.post { Toast.makeText(this, "图像处理失败", Toast.LENGTH_SHORT).show() }
                        return@postDelayed
                    }

                    val safeRect = Rect(
                        rect.left.coerceIn(0, bitmap.width),
                        rect.top.coerceIn(0, bitmap.height),
                        rect.right.coerceIn(0, bitmap.width),
                        rect.bottom.coerceIn(0, bitmap.height)
                    )

                    if (safeRect.width() <= 0 || safeRect.height() <= 0) {
                        handler.post { Toast.makeText(this, "选区无效", Toast.LENGTH_SHORT).show() }
                        bitmap.recycle()
                        return@postDelayed
                    }

                    val cropped = Bitmap.createBitmap(bitmap, safeRect.left, safeRect.top, safeRect.width(), safeRect.height())
                    bitmap.recycle()
                    
                    handler.post { Toast.makeText(this, "正在识别...", Toast.LENGTH_SHORT).show() }
                    processBitmap(cropped)

                } catch (e: Exception) {
                    image.close()
                    Log.e(TAG, "Process image error", e)
                    handler.post { Toast.makeText(this, "截图异常：${e.message}", Toast.LENGTH_SHORT).show() }
                }
            }
        }, 300) // 延迟缩短到300ms，因为框选层已经移除了
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        return try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width

            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Image to bitmap failed", e)
            null
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
                        startActivity(Intent(this@ScreenCaptureService, SearchResultActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            putExtra("question", question?.content ?: text)
                            putExtra("answer", question?.answer ?: "未在题库中找到匹配题目")
                            putExtra("analysis", question?.analysis ?: "")
                        })
                    }
                } else {
                    handler.post { Toast.makeText(this@ScreenCaptureService, "未识别到文字", Toast.LENGTH_SHORT).show() }
                }
            } catch (e: Exception) {
                Log.e(TAG, "OCR failed", e)
                handler.post { Toast.makeText(this@ScreenCaptureService, "识别失败", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        isInitialized = false
        ScreenCaptureService.isInitialized = false
        try { unregisterReceiver(captureReceiver) } catch (_: Exception) {}
        virtualDisplay?.release()
        mediaProjection?.stop()
        imageReader?.close()
        ocrManager.close()
        Log.d(TAG, "Service destroyed")
    }
}