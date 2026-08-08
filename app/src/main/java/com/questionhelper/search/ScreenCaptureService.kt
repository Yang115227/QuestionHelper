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
import android.os.*
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
    private val mainHandler = Handler(Looper.getMainLooper())
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null
    private var metrics: DisplayMetrics? = null

    companion object {
        @Volatile
        var isRunning = false
        @Volatile
        var isInitialized = false  // 改为 public，供 FloatWindowService 检查
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

        // 关键修复：创建后台线程处理 ImageReader
        backgroundThread = HandlerThread("ScreenCaptureThread", Process.THREAD_PRIORITY_BACKGROUND)
        backgroundThread?.start()
        backgroundHandler = Handler(backgroundThread!!.looper)

        ocrManager = OcrManager(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: isInitialized=$isInitialized")

        if (!isInitialized) {
            if (intent == null) {
                // 系统重启服务但没有录屏数据，直接停止
                isRunning = false
                stopSelf()
                return START_NOT_STICKY
            }
            val code = intent.getIntExtra("result_code", 0)
            val data = intent.getParcelableExtra<Intent>("result_data")
            if (code != 0 && data != null) {
                initMediaProjection(code, data)
            } else {
                mainHandler.post { Toast.makeText(this, "录屏数据缺失，请重新授权", Toast.LENGTH_SHORT).show() }
                isRunning = false
                stopSelf()
                return START_NOT_STICKY
            }
        }

        // 处理直接截图请求（替代广播）
        if (intent?.action == "CAPTURE") {
            val rect = intent.getParcelableExtra<Rect>("rect")
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
                mainHandler.post { Toast.makeText(this, "录屏初始化失败", Toast.LENGTH_SHORT).show() }
                return
            }

            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    super.onStop()
                    Log.d(TAG, "MediaProjection stopped")
                    isInitialized = false
                }
            }, mainHandler)

            setupImageReader()
            isInitialized = true
            Log.d(TAG, "MediaProjection initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Init failed", e)
            mainHandler.post { Toast.makeText(this, "录屏初始化失败：${e.message}", Toast.LENGTH_LONG).show() }
        }
    }

    private fun setupImageReader() {
        metrics = DisplayMetrics()
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getRealMetrics(metrics)

        imageReader = ImageReader.newInstance(
            metrics!!.widthPixels, metrics!!.heightPixels, PixelFormat.RGBA_8888, 2
        )

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            metrics!!.widthPixels, metrics!!.heightPixels, metrics!!.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, backgroundHandler
        )
        Log.d(TAG, "VirtualDisplay created")
    }

    private fun captureArea(rect: Rect) {
        Log.d(TAG, "captureArea called, isInitialized=$isInitialized")
        if (!isInitialized || imageReader == null) {
            mainHandler.post {
                Toast.makeText(this, "录屏服务未就绪，请重新授权", Toast.LENGTH_SHORT).show()
            }
            return
        }

        mainHandler.post { Toast.makeText(this, "正在截图...", Toast.LENGTH_SHORT).show() }

        // 延迟确保框选层已完全消失
        mainHandler.postDelayed({
            try {
                // 添加重试机制，解决图像未就绪问题
                var image: Image? = null
                var retryCount = 0
                while (image == null && retryCount < 5) {
                    image = imageReader?.acquireLatestImage()
                    if (image == null) {
                        Thread.sleep(200)
                        retryCount++
                    }
                }

                if (image == null) {
                    mainHandler.post {
                        Toast.makeText(this, "截图失败：无法获取屏幕图像", Toast.LENGTH_SHORT).show()
                    }
                    return@postDelayed
                }

                val bitmap = imageToBitmap(image)
                image.close()

                bitmap?.let { bmp ->
                    val safeRect = Rect(
                        rect.left.coerceIn(0, bmp.width),
                        rect.top.coerceIn(0, bmp.height),
                        rect.right.coerceIn(0, bmp.width),
                        rect.bottom.coerceIn(0, bmp.height)
                    )

                    if (safeRect.width() > 0 && safeRect.height() > 0) {
                        val cropped = Bitmap.createBitmap(
                            bmp, safeRect.left, safeRect.top,
                            safeRect.width(), safeRect.height()
                        )
                        mainHandler.post { Toast.makeText(this, "正在识别...", Toast.LENGTH_SHORT).show() }
                        processBitmap(cropped)
                    } else {
                        mainHandler.post {
                            Toast.makeText(this, "选区无效，请重新框选", Toast.LENGTH_SHORT).show()
                        }
                    }
                    bmp.recycle()  // 确保释放原图
                } ?: run {
                    mainHandler.post {
                        Toast.makeText(this, "图像转换失败", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Capture failed", e)
                mainHandler.post {
                    Toast.makeText(this, "截图异常：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }, 300)  // 延迟可以缩短到 300ms
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
                    mainHandler.post { Toast.makeText(this@ScreenCaptureService, "未识别到文字，请重新框选", Toast.LENGTH_LONG).show() }
                    return@launch
                }

                mainHandler.post { Toast.makeText(this@ScreenCaptureService, "识别成功，正在搜索...", Toast.LENGTH_SHORT).show() }

                val repo = QuestionRepository(QuestionApp.database.questionDao())
                val question = repo.searchQuestion(text.take(50))

                mainHandler.post {
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
                mainHandler.post { Toast.makeText(this@ScreenCaptureService, "识别失败：${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        isInitialized = false  // 重置初始化状态
        virtualDisplay?.release()
        mediaProjection?.stop()
        imageReader?.close()
        ocrManager.close()
        backgroundThread?.quitSafely()
        Log.d(TAG, "Service destroyed")
    }
}