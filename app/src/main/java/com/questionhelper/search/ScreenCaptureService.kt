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

    // 缓存最新帧
    private var cachedImage: Image? = null
    private val imageLock = Object()

    companion object {
        @Volatile
        var isRunning = false
        private const val TAG = "ScreenCapture"
        private const val CHANNEL_ID = "screen_capture"
        private const val NOTIFICATION_ID = 1001

        fun requestPermission(activity: android.app.Activity) {
            val manager = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            activity.startActivityForResult(manager.createScreenCaptureIntent(), 1001)
        }
    }

    private val captureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "com.questionhelper.CAPTURE_SCREEN") {
                val rect = intent.getParcelableExtra<Rect>("rect")
                Log.d(TAG, "Broadcast received, rect=$rect")
                rect?.let { captureArea(it) }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        ocrManager = OcrManager(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        val filter = IntentFilter("com.questionhelper.CAPTURE_SCREEN")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(captureReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(captureReceiver, filter)
        }
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: isInitialized=$isInitialized, intent=${intent != null}")
        if (!isInitialized && intent != null) {
            val code = intent.getIntExtra("result_code", 0)
            val data = intent.getParcelableExtra<Intent>("result_data")
            if (code != 0 && data != null) {
                initMediaProjection(code, data)
            } else {
                handler.post { Toast.makeText(this, "录屏数据缺失，请重新授权", Toast.LENGTH_SHORT).show() }
            }
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
            .setContentText("录屏搜题服务运行中，点击悬浮球即可截图")
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
                handler.post { Toast.makeText(this, "录屏初始化失败", Toast.LENGTH_SHORT).show() }
                return
            }
            
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    super.onStop()
                    Log.d(TAG, "MediaProjection stopped by system")
                    isInitialized = false
                }
            }, handler)
            
            setupImageReader()
            isInitialized = true
            Log.d(TAG, "MediaProjection initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Init failed", e)
            handler.post { Toast.makeText(this, "录屏初始化失败：${e.message}", Toast.LENGTH_LONG).show() }
        }
    }

    private fun setupImageReader() {
        metrics = DisplayMetrics()
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getRealMetrics(metrics)

        imageReader = ImageReader.newInstance(
            metrics!!.widthPixels, metrics!!.heightPixels, PixelFormat.RGBA_8888, 3
        )

        // 关键修复：持续监听新帧，缓存最新图像
        imageReader?.setOnImageAvailableListener({ reader ->
            synchronized(imageLock) {
                try {
                    cachedImage?.close()
                    cachedImage = reader.acquireLatestImage()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to acquire image", e)
                }
            }
        }, handler)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            metrics!!.widthPixels, metrics!!.heightPixels, metrics!!.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, handler
        )
        Log.d(TAG, "VirtualDisplay created: ${metrics!!.widthPixels}x${metrics!!.heightPixels}")
    }

    private fun captureArea(rect: Rect) {
        Log.d(TAG, "captureArea called, isInitialized=$isInitialized")
        if (!isInitialized) {
            handler.post { Toast.makeText(this, "录屏服务未就绪，请重新授权", Toast.LENGTH_SHORT).show() }
            return
        }
        
        handler.post { Toast.makeText(this, "正在截图...", Toast.LENGTH_SHORT).show() }

        // 延迟等待框选层消失，然后从缓存取帧
        handler.postDelayed({
            synchronized(imageLock) {
                val image = cachedImage
                cachedImage = null  // 取走，防止重复使用
                
                if (image == null) {
                    Log.e(TAG, "No cached image available")
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

                    // 裁剪
                    val safeRect = Rect(
                        rect.left.coerceIn(0, bitmap.width),
                        rect.top.coerceIn(0, bitmap.height),
                        rect.right.coerceIn(0, bitmap.width),
                        rect.bottom.coerceIn(0, bitmap.height)
                    )

                    if (safeRect.width() <= 0 || safeRect.height() <= 0) {
                        handler.post { Toast.makeText(this, "选区无效，请重新框选", Toast.LENGTH_SHORT).show() }
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
        }, 500)
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

                if (text.isEmpty()) {
                    handler.post { Toast.makeText(this@ScreenCaptureService, "未识别到文字，请重新框选清晰区域", Toast.LENGTH_LONG).show() }
                    return@launch
                }

                handler.post { Toast.makeText(this@ScreenCaptureService, "识别成功，正在搜索...", Toast.LENGTH_SHORT).show() }

                val repo = QuestionRepository(QuestionApp.database.questionDao())
                val question = repo.searchQuestion(text.take(50))

                handler.post {
                    val intent = Intent(this@ScreenCaptureService, SearchResultActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        putExtra("question", question?.content ?: text)
                        putExtra("answer", question?.answer ?: "未在题库中找到匹配题目")
                        putExtra("analysis", question?.analysis ?: "")
                    }
                    startActivity(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "OCR failed", e)
                handler.post { Toast.makeText(this@ScreenCaptureService, "识别失败：${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        isInitialized = false
        try { unregisterReceiver(captureReceiver) } catch (_: Exception) {}
        synchronized(imageLock) {
            cachedImage?.close()
            cachedImage = null
        }
        virtualDisplay?.release()
        mediaProjection?.stop()
        imageReader?.close()
        ocrManager.close()
        Log.d(TAG, "Service destroyed")
    }
}