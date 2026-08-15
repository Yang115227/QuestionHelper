package com.questionhelper.search

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.*
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.questionhelper.MainActivity
import com.questionhelper.R

class FloatWindowService : Service() {
    private lateinit var windowManager: WindowManager
    private var floatBall: View? = null
    private var cropView: CropOverlayView? = null
    private var resultView: FloatResultView? = null
    private var isShowingCrop = false
    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private var receiverRegistered = false

    @Volatile
    private var waitingScreenCapture = false

    companion object {
        private const val TAG = "FloatWindow"
        private const val PREFS_NAME = "crop_prefs"
        private const val KEY_CROP_RECT = "crop_rect"
        private const val CHANNEL_ID = "float_window"
        private const val NOTIFICATION_ID = 1002

        const val ACTION_SHOW_RESULT = "com.questionhelper.SHOW_RESULT"
        const val EXTRA_QUESTION = "question"
        const val EXTRA_ANSWER = "answer"
        const val EXTRA_ANALYSIS = "analysis"
        const val EXTRA_MATCHED = "matched"

        fun checkPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else true
        }

        fun start(context: Context) {
            if (!checkPermission(context)) {
                Toast.makeText(context, R.string.permission_overlay_denied, Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return
            }
            val intent = Intent(context, FloatWindowService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val serviceStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ScreenCaptureService.ACTION_PROJECTION_READY -> {
                    Log.d(TAG, "Screen capture service ready")
                    waitingScreenCapture = false
                }
                ScreenCaptureService.ACTION_PROJECTION_STOPPED -> {
                    val error = intent.getStringExtra(ScreenCaptureService.EXTRA_ERROR)
                    Log.d(TAG, "Screen capture service stopped: $error")
                    waitingScreenCapture = false
                    ScreenCaptureService.markProjectionStopped()
                    if (!error.isNullOrEmpty()) {
                        handler.post { Toast.makeText(context, error, Toast.LENGTH_LONG).show() }
                    }
                }
                ACTION_SHOW_RESULT -> {
                    val question = intent.getStringExtra(EXTRA_QUESTION) ?: ""
                    val answer = intent.getStringExtra(EXTRA_ANSWER) ?: ""
                    val analysis = intent.getStringExtra(EXTRA_ANALYSIS) ?: ""
                    val matched = intent.getBooleanExtra(EXTRA_MATCHED, false)
                    Log.d(TAG, "Show result received: matched=$matched")
                    handler.post {
                        showResult(question, answer, analysis, matched)
                    }
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
        startForeground(NOTIFICATION_ID, createNotification())

        registerServiceStateReceiver()
        showFloatBall()
    }

    private fun registerServiceStateReceiver() {
        try {
            val filter = IntentFilter().apply {
                addAction(ScreenCaptureService.ACTION_PROJECTION_READY)
                addAction(ScreenCaptureService.ACTION_PROJECTION_STOPPED)
                addAction(ACTION_SHOW_RESULT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(serviceStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(serviceStateReceiver, filter)
            }
            receiverRegistered = true
            Log.d(TAG, "Service state receiver registered")
        } catch (e: Throwable) {
            Log.e(TAG, "Register service state receiver failed", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, getString(R.string.float_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = getString(R.string.float_channel_desc) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.float_service_running))
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setOngoing(true)
            .build()
    }

    private fun showFloatBall() {
        val params = WindowManager.LayoutParams(
            dpToPx(60), dpToPx(60),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dpToPx(20)
            y = dpToPx(200)
        }

        val button = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_search)
            background = createCircleBackground()
            alpha = 0.9f
            setOnTouchListener(FloatBallTouchListener(params, this))
        }

        floatBall = button
        try {
            windowManager.addView(button, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add float ball", e)
        }
    }

    private fun createCircleBackground(): android.graphics.drawable.Drawable {
        val drawable = android.graphics.drawable.GradientDrawable()
        drawable.shape = android.graphics.drawable.GradientDrawable.OVAL
        drawable.setColor(ContextCompat.getColor(this, android.R.color.holo_blue_light))
        return drawable
    }

    private fun onFloatBallClick() {
        if (isShowingCrop) {
            hideCropView()
            return
        }

        val hasScreenCapture = ScreenCaptureService.isRunning
        val isScreenCaptureInitializing = ScreenCaptureService.isInitializing
        val hasAccessibility = isAccessibilityServiceEnabled()

        when {
            isScreenCaptureInitializing && !hasAccessibility -> {
                Toast.makeText(this, R.string.screenshot_service_initializing, Toast.LENGTH_SHORT).show()
                return
            }
            hasScreenCapture && !ScreenCaptureService.isInitialized -> {
                Toast.makeText(this, R.string.screenshot_service_initializing, Toast.LENGTH_SHORT).show()
                handler.postDelayed({
                    if (ScreenCaptureService.isInitialized) {
                        showCropView()
                    } else {
                        onFloatBallClick()
                    }
                }, 800)
                return
            }
            !hasScreenCapture && !hasAccessibility -> {
                Toast.makeText(this, R.string.float_select_capture_method, Toast.LENGTH_SHORT).show()
                val intent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("show_capture_choice", true)
                }
                startActivity(intent)
                waitingScreenCapture = true
                return
            }
        }

        showCropView()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return try {
            val enabledServices = Settings.Secure.getString(
                contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val componentName = android.content.ComponentName(
                this, AccessibilitySearchService::class.java
            ).flattenToString()
            enabledServices.contains(componentName)
        } catch (e: Exception) { false }
    }

    private fun showCropView() {
        if (isShowingCrop) return
        isShowingCrop = true

        // 先移除旧的结果窗口，但避免其 onDismiss 恢复悬浮球
        resultView?.onDismiss = null   // 禁用回调
        resultView?.dismiss()
        resultView = null

        // 再隐藏悬浮球
        floatBall?.visibility = View.GONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        val savedRect = loadCropRect()

        cropView = CropOverlayView(this).apply {
            if (savedRect != null) setInitialRect(savedRect)
            onCropConfirmed = { rect ->
                saveCropRect(rect)
                removeCropViewOnly()
                isShowingCrop = false
                floatBall?.visibility = View.VISIBLE
                captureAndSearch(rect)
            }
            onCropCanceled = { hideCropView() }
        }

        try {
            windowManager.addView(cropView, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show crop view", e)
            isShowingCrop = false
            floatBall?.visibility = View.VISIBLE
        }
    }

    private fun removeCropViewOnly() {
        cropView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) { Log.e(TAG, "Remove crop view failed", e) }
            cropView = null
        }
    }

    private fun hideCropView() {
        isShowingCrop = false
        removeCropViewOnly()
        floatBall?.visibility = View.VISIBLE
    }

    fun showResult(question: String, answer: String, analysis: String, isMatched: Boolean) {
        try {
            if (isShowingCrop) {
                hideCropView()
            }

            // 移除旧结果窗口，同时禁用回调避免误恢复悬浮球
            resultView?.onDismiss = null
            resultView?.dismiss()
            resultView = null

            // 确保悬浮球隐藏
            floatBall?.visibility = View.GONE

            // 创建新窗口，设置回调
            resultView = FloatResultView(this).apply {
                onDismiss = {
                    floatBall?.visibility = View.VISIBLE
                }
            }
            resultView?.show(question, answer, analysis, isMatched)
        } catch (e: Exception) {
            Log.e(TAG, "showResult failed", e)
            floatBall?.visibility = View.VISIBLE
            Toast.makeText(this, "显示结果失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun captureAndSearch(rect: Rect) {
        Log.d(TAG, "captureAndSearch: $rect")
        try {
            when {
                ScreenCaptureService.isRunning && ScreenCaptureService.isInitialized -> {
                    val intent = Intent(this, ScreenCaptureService::class.java).apply {
                        action = ScreenCaptureService.ACTION_CAPTURE
                        putExtra("rect", rect)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ContextCompat.startForegroundService(this, intent)
                    } else {
                        startService(intent)
                    }
                }
                isAccessibilityServiceEnabled() -> {
                    val intent = Intent(this, AccessibilitySearchService::class.java).apply {
                        action = "CAPTURE"
                        putExtra("rect", rect)
                    }
                    startService(intent)
                }
                else -> {
                    showResult(
                        "⚠️ 截图服务未就绪",
                        "录屏权限可能已被系统回收，或无障碍服务未开启。\n请重新点击「悬浮搜题」选择截图方式。",
                        "",
                        false
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "captureAndSearch failed", e)
            showResult(
                "⚠️ 启动截图失败",
                "错误信息：${e.message}",
                "",
                false
            )
        }
    }

    private fun saveCropRect(rect: Rect) {
        prefs.edit().putString(KEY_CROP_RECT, "${rect.left},${rect.top},${rect.right},${rect.bottom}").apply()
    }

    private fun loadCropRect(): Rect? {
        val str = prefs.getString(KEY_CROP_RECT, null) ?: return null
        val parts = str.split(",")
        return if (parts.size == 4) {
            Rect(parts[0].toInt(), parts[1].toInt(), parts[2].toInt(), parts[3].toInt())
        } else null
    }

    private inner class FloatBallTouchListener(
        private val params: WindowManager.LayoutParams,
        private val view: View
    ) : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var touchX = 0f
        private var touchY = 0f
        private var isClick = false
        private val clickThreshold = 15f

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    isClick = true
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (kotlin.math.abs(dx) > clickThreshold || kotlin.math.abs(dy) > clickThreshold) {
                        isClick = false
                    }
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    windowManager.updateViewLayout(view, params)
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) onFloatBallClick()
                    return true
                }
            }
            return false
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        super.onDestroy()
        hideCropView()
        resultView?.onDismiss = null
        resultView?.dismiss()
        floatBall?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            floatBall = null
        }
        if (receiverRegistered) {
            try { unregisterReceiver(serviceStateReceiver) } catch (_: Exception) {}
        }
    }
}