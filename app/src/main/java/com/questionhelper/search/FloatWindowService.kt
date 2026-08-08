package com.questionhelper.search

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.*
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.questionhelper.MainActivity

class FloatWindowService : Service() {
    private lateinit var windowManager: WindowManager
    private var floatBall: View? = null
    private var cropView: CropOverlayView? = null
    private var isShowingCrop = false

    companion object {
        private const val TAG = "FloatWindow"
        
        fun checkPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else true
        }
        
        fun start(context: Context) {
            if (!checkPermission(context)) {
                Toast.makeText(context, "请先开启悬浮窗权限", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return
            }
            context.startService(Intent(context, FloatWindowService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        showFloatBall()
    }

    private fun showFloatBall() {
        val params = WindowManager.LayoutParams(
            dpToPx(60),
            dpToPx(60),
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
            Log.d(TAG, "Float ball added")
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
        Log.d(TAG, "Float ball clicked, isShowingCrop=$isShowingCrop")
        
        if (isShowingCrop) {
            hideCropView()
            return
        }

        val hasScreenCapture = ScreenCaptureService.isRunning
        val hasAccessibility = isAccessibilityServiceEnabled()

        Log.d(TAG, "hasScreenCapture=$hasScreenCapture, hasAccessibility=$hasAccessibility")

        if (!hasScreenCapture && !hasAccessibility) {
            Toast.makeText(this, "请先选择截图方式", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("show_capture_choice", true)
            }
            startActivity(intent)
            return
        }

        showCropView()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return try {
            val enabledServices = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            
            val componentName = android.content.ComponentName(
                this, 
                AccessibilitySearchService::class.java
            ).flattenToString()
            
            enabledServices.contains(componentName)
        } catch (e: Exception) {
            false
        }
    }

    private fun showCropView() {
        if (isShowingCrop) return
        isShowingCrop = true

        // 隐藏悬浮球
        floatBall?.visibility = View.GONE
        Log.d(TAG, "Float ball hidden, showing crop view")

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

        cropView = CropOverlayView(this).apply {
            onCropConfirmed = { rect ->
                Log.d(TAG, "Crop confirmed, rect=$rect")
                hideCropView()
                captureAndSearch(rect)
            }
            onCropCanceled = {
                Log.d(TAG, "Crop canceled")
                hideCropView()
            }
        }

        try {
            windowManager.addView(cropView, params)
            Log.d(TAG, "Crop view shown")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show crop view", e)
            isShowingCrop = false
            floatBall?.visibility = View.VISIBLE
            Toast.makeText(this, "框选层显示失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hideCropView() {
        isShowingCrop = false
        cropView?.let {
            try {
                windowManager.removeView(it)
                Log.d(TAG, "Crop view removed")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove crop view", e)
            }
            cropView = null
        }
        floatBall?.visibility = View.VISIBLE
        Log.d(TAG, "Float ball visible again")
    }

    private fun captureAndSearch(rect: Rect) {
        Log.d(TAG, "captureAndSearch: $rect")
        
        if (ScreenCaptureService.isRunning) {
            // 关键修复：先 startService 确保 Service 真的活着（防止被系统休眠）
            startService(Intent(this, ScreenCaptureService::class.java))
            
            Toast.makeText(this, "正在截图识别...", Toast.LENGTH_SHORT).show()
            sendBroadcast(Intent("com.questionhelper.CAPTURE_SCREEN").apply {
                putExtra("rect", rect)
            })
        } else if (isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "正在截图识别...", Toast.LENGTH_SHORT).show()
            sendBroadcast(Intent("com.questionhelper.ACCESSIBILITY_CAPTURE").apply {
                putExtra("rect", rect)
            })
        } else {
            Toast.makeText(this, "截图服务未运行，请重新开启", Toast.LENGTH_SHORT).show()
        }
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
                    if (isClick) {
                        onFloatBallClick()
                    }
                    return true
                }
            }
            return false
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        hideCropView()
        floatBall?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove float ball", e)
            }
            floatBall = null
        }
    }
}