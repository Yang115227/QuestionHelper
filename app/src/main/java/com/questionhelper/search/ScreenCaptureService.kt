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

    companion object {
        @Volatile
        var isRunning = false
        private const val TAG = "ScreenCapture"
        private const val CHANNEL_ID = "screen_capture"
        private const val NOTIFICATION_ID = 1001
        private var resultCode = 0
        private var resultData: Intent? = null

        fun setResult(code: Int, data: Intent) {
            resultCode = code
            resultData = data
            Log.d(TAG, "MediaProjection result set: code=$code")
        }

        fun requestPermission(activity: android.app.Activity) {
            val manager = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            activity.startActivityForResult(manager.createScreenCaptureIntent(), 1001)
        }
    }

    private val captureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "com.questionhelper.CAPTURE_SCREEN") {
                val rect = intent.getParcelableExtra<Rect>("rect")
                Log.d(TAG, "Received capture request for rect: $rect")
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
        
        initMediaProjection()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "录屏搜题服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持录屏搜题服务运行"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("搜题助手")
            .setContentText("录屏搜题服务运行中，点击悬浮球即可截图搜题")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setOngoing(true)
            .build()
    }

    private fun initMediaProjection() {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        resultData?.let { data ->
            try {
                mediaProjection = manager.getMediaProjection(resultCode, data)
                setupImageReader()
                Log.d(TAG, "MediaProjection initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize MediaProjection", e)
                Toast.makeText(this, "录屏初始化失败", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Log.e(TAG, "No MediaProjection result data available")
            Toast.makeText(this, "请先授权录屏权限", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupImageReader() {
        metrics = DisplayMetrics()
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = windowManager.defaultDisplay
        display.getRealMetrics(metrics)

        imageReader = ImageReader.newInstance(
            metrics!!.widthPixels,
            metrics!!.heightPixels,
            PixelFormat.RGBA_8888,
            2
        )

        mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            metrics!!.widthPixels,
            metrics!!.heightPixels,
            metrics!!.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            handler
        )?.also { 
            virtualDisplay = it
            Log.d(TAG, "VirtualDisplay created: ${metrics!!.widthPixels}x${metrics!!.heightPixels}")
        }
    }

    private fun captureArea(rect: Rect) {
        handler.postDelayed({
            try {
                val image = imageReader?.acquireLatestImage()
                image?.let { img ->
                    val bitmap = imageToBitmap(img)
                    img.close()
                    
                    bitmap?.let { bmp ->
                        val safeRect = Rect(
                            rect.left.coerceIn(0, bmp.width),
                            rect.top.coerceIn(0, bmp.height),
                            rect.right.coerceIn(0, bmp.width),
                            rect.bottom.coerceIn(0, bmp.height)
                        )
                        
                        if (safeRect.width() > 0 && safeRect.height() > 0) {
                            val cropped = Bitmap.createBitmap(bmp, safeRect.left, safeRect.top, safeRect.width(), safeRect.height())
                            bmp.recycle()
                            processBitmap(cropped)
                        } else {
                            Log.e(TAG, "Invalid crop rect: $safeRect, bitmap: ${bmp.width}x${bmp.height}")
                            bmp.recycle()
                        }
                    }
                } ?: run {
                    Log.e(TAG, "Failed to acquire image from ImageReader")
                    handler.post {
                        Toast.makeText(this@ScreenCaptureService, "截图失败，请重试", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Capture failed", e)
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
                image.width + rowPadding / pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888
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
                        val intent = Intent(this@ScreenCaptureService, SearchResultActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            putExtra("question", question?.content ?: text)
                            putExtra("answer", question?.answer ?: "未在题库中找到匹配题目")
                            putExtra("analysis", question?.analysis ?: "")
                        }
                        startActivity(intent)
                    }
                } else {
                    handler.post {
                        Toast.makeText(this@ScreenCaptureService, "未识别到文字", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "OCR failed", e)
                handler.post {
                    Toast.makeText(this@ScreenCaptureService, "识别失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        try {
            unregisterReceiver(captureReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Unregister receiver failed", e)
        }
        virtualDisplay?.release()
        mediaProjection?.stop()
        imageReader?.close()
        ocrManager.close()
        Log.d(TAG, "Service destroyed")
    }
}