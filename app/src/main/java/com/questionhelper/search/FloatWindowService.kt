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
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.questionhelper.R

class FloatWindowService : Service() {
    private lateinit var windowManager: WindowManager
    private var floatBall: View? = null
    private var cropView: CropOverlayView? = null
    private var isShowingCrop = false

    companion object {
        fun checkPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else true
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (!checkPermission(this)) {
            Toast.makeText(this, "请先开启悬浮窗权限", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }
        showFloatBall()
    }

    private fun showFloatBall() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        val button = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_search)
            background = ContextCompat.getDrawable(context, android.R.drawable.ic_menu_crop)
            setBackgroundColor(ContextCompat.getColor(context, android.R.color.holo_blue_light))
            alpha = 0.8f
            setOnClickListener {
                if (isShowingCrop) {
                    hideCropView()
                } else {
                    showCropView()
                }
            }
            setOnTouchListener(FloatBallTouchListener(params))
        }

        floatBall = button
        windowManager.addView(button, params)
    }

    private fun showCropView() {
        isShowingCrop = true
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT
        )

        cropView = CropOverlayView(this).apply {
            onCropConfirmed = { rect ->
                hideCropView()
                captureAndSearch(rect)
            }
            onCropCanceled = {
                hideCropView()
            }
        }

        windowManager.addView(cropView, params)
    }

    private fun hideCropView() {
        isShowingCrop = false
        cropView?.let {
            windowManager.removeView(it)
            cropView = null
        }
    }

    private fun captureAndSearch(rect: Rect) {
        // 优先使用录屏，其次无障碍
        if (ScreenCaptureService.isRunning) {
            sendBroadcast(Intent("com.questionhelper.CAPTURE_SCREEN").apply {
                putExtra("rect", rect)
            })
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 尝试无障碍截图
            sendBroadcast(Intent("com.questionhelper.ACCESSIBILITY_CAPTURE").apply {
                putExtra("rect", rect)
            })
        } else {
            Toast.makeText(this, "请先开启录屏或无障碍服务", Toast.LENGTH_SHORT).show()
        }
    }

    private inner class FloatBallTouchListener(
        private val params: WindowManager.LayoutParams
    ) : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var touchX = 0f
        private var touchY = 0f

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - touchX).toInt()
                    params.y = initialY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(v, params)
                    return true
                }
            }
            return false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hideCropView()
        floatBall?.let {
            windowManager.removeView(it)
            floatBall = null
        }
    }
}
