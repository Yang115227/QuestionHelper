package com.questionhelper.search

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.questionhelper.R

class ScreenCaptureService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())
    private val tag = "ScreenCaptureService"

    companion object {
        @Volatile
        var isRunning = false
            private set

        @Volatile
        var isInitialized = false
            private set

        const val ACTION_PROJECTION_STOPPED = "com.questionhelper.action.PROJECTION_STOPPED"

        /**
         * 供外部（如 MainActivity 广播接收器）标记授权已失效
         */
        fun markProjectionStopped() {
            isInitialized = false
        }

        /**
         * 启动服务（带参数）
         */
        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                putExtra("result_code", resultCode)
                putExtra("result_data", data)
            }
            context.startForegroundService(intent)
        }

        /**
         * 停止服务
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, ScreenCaptureService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // 服务被系统重启，但无参数 —— 无法恢复 MediaProjection
            Log.w(tag, "Service restarted by system without intent, stopping self")
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent.getIntExtra("result_code", -1)
        val data = intent.getParcelableExtra<Intent>("result_data")

        if (resultCode == -1 || data == null) {
            Log.e(tag, "Invalid MediaProjection data")
            stopSelf()
            return START_NOT_STICKY
        }

        // 如果已经初始化，先释放旧的
        releaseProjection()

        if (!initializeProjection(resultCode, data)) {
            Log.e(tag, "MediaProjection initialization failed")
            stopSelf()
            return START_NOT_STICKY
        }

        isRunning = true
        isInitialized = true
        return START_STICKY
    }

    /**
     * 初始化 MediaProjection 和 VirtualDisplay
     */
    private fun initializeProjection(resultCode: Int, data: Intent): Boolean {
        return try {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = manager.getMediaProjection(resultCode, data)

            if (mediaProjection == null) {
                Log.e(tag, "getMediaProjection returned null")
                return false
            }

            // 监听 MediaProjection 停止事件（用户撤销授权或超时）
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.w(tag, "MediaProjection stopped by system")
                    isInitialized = false
                    releaseProjection()
                    // 通知 MainActivity 重新申请权限
                    sendBroadcast(Intent(ACTION_PROJECTION_STOPPED))
                }
            }, handler)

            // 创建 ImageReader（根据实际屏幕尺寸调整）
            val width = 1080
            val height = 1920
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                width, height, resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, handler
            )

            true
        } catch (e: Exception) {
            Log.e(tag, "Initialize projection failed", e)
            false
        }
    }

    /**
     * 获取最新一帧截图
     */
    fun captureScreen(): Bitmap? {
        if (!isInitialized || imageReader == null) {
            Log.w(tag, "Capture called but not initialized")
            return null
        }
        return try {
            val image = imageReader?.acquireLatestImage() ?: return null
            // ... 将 Image 转为 Bitmap 的逻辑（保留你原有代码）
            // 注意：使用完后要 image.close()
            null // 占位，替换为你的实际转换代码
        } catch (e: Exception) {
            Log.e(tag, "Capture failed", e)
            null
        }
    }

    /**
     * 释放所有资源
     */
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
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseProjection()
        isRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ========== 前台服务通知 ==========

    private fun startForeground() {
        val channelId = "screen_capture"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "录屏服务",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("搜题助手")
            .setContentText("录屏截图服务运行中")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
        startForeground(1, notification)
    }

}
