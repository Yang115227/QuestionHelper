package com.questionhelper.search

import android.content.Context
import android.content.SharedPreferences
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat

class CropOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var onCropConfirmed: ((Rect) -> Unit)? = null
    var onCropCanceled: (() -> Unit)? = null

    private val prefs: SharedPreferences = context.getSharedPreferences("crop_prefs", Context.MODE_PRIVATE)

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2196F3")
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2196F3")
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private var cropRect = Rect()
    private val minCropSize = dpToPx(80)
    private val handleRadius = dpToPx(18).toFloat()

    private enum class TouchMode { NONE, DRAG, RESIZE }
    private var touchMode = TouchMode.NONE
    private var lastX = 0f
    private var lastY = 0f

    private lateinit var closeBtn: ImageButton
    private lateinit var resetBtn: ImageButton
    private lateinit var hideBtn: ImageButton
    private lateinit var resizeHandle: ImageButton
    private lateinit var confirmBtn: Button

    private val btnSize = dpToPx(36)
    private val confirmWidth = dpToPx(120)
    private val confirmHeight = dpToPx(44)

    init {
        setWillNotDraw(false)
        setBackgroundColor(Color.TRANSPARENT)
        setupButtons()
        post {
            loadCropRect()
            if (cropRect.isEmpty) resetCropRect()
            updateButtonPositions()
            invalidate()
        }
    }

    private fun setupButtons() {
        // 关闭按钮：半透明蓝背景 + 白色图标
        closeBtn = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            background = createCircleButtonBackground()
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setOnClickListener { onCropCanceled?.invoke() }
        }

        // 重置按钮：半透明蓝背景 + 白色图标
        resetBtn = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_menu_revert)
            background = createCircleButtonBackground()
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setOnClickListener {
                resetCropRect()
                updateButtonPositions()
            }
        }

        // 隐藏按钮：半透明蓝背景 + 白色图标
        hideBtn = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_menu_view)
            background = createCircleButtonBackground()
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setOnClickListener { onCropCanceled?.invoke() }
        }

        // 缩放手柄：半透明蓝背景 + 白色图标
        resizeHandle = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_menu_crop)
            background = createCircleButtonBackground()
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setOnTouchListener(ResizeHandleTouchListener())
        }

        // 确认按钮保持半透明蓝背景
        confirmBtn = Button(context).apply {
            text = "确认搜题"
            setTextColor(Color.WHITE)
            textSize = 16f
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = confirmHeight / 2f
                setColor(Color.parseColor("#802196F3"))
            }
            setOnClickListener {
                if (cropRect.width() >= minCropSize && cropRect.height() >= minCropSize) {
                    text = "正在识别..."
                    isEnabled = false
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = confirmHeight / 2f
                        setColor(Color.parseColor("#80757575"))
                    }
                    saveCropRect()
                    onCropConfirmed?.invoke(cropRect)
                }
            }
        }

        addView(closeBtn)
        addView(resetBtn)
        addView(hideBtn)
        addView(resizeHandle)
        addView(confirmBtn)
    }

    private fun createCircleButtonBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#802196F3")) // 半透明蓝
        }
    }

    private fun updateButtonPositions() {
        if (!this::closeBtn.isInitialized) return
        val left = cropRect.left
        val top = cropRect.top
        val right = cropRect.right
        val bottom = cropRect.bottom

        closeBtn.layoutParams = FrameLayout.LayoutParams(btnSize, btnSize).apply {
            leftMargin = right - btnSize
            topMargin = top
        }
        closeBtn.visibility = View.VISIBLE

        resetBtn.layoutParams = FrameLayout.LayoutParams(btnSize, btnSize).apply {
            leftMargin = left
            topMargin = top
        }
        resetBtn.visibility = View.VISIBLE

        hideBtn.layoutParams = FrameLayout.LayoutParams(btnSize, btnSize).apply {
            leftMargin = left
            topMargin = bottom - btnSize
        }
        hideBtn.visibility = View.VISIBLE

        resizeHandle.layoutParams = FrameLayout.LayoutParams(btnSize, btnSize).apply {
            leftMargin = right - btnSize / 2
            topMargin = bottom - btnSize / 2
        }
        resizeHandle.visibility = View.VISIBLE

        val confirmLeft = (left + right) / 2 - confirmWidth / 2
        val confirmTop = bottom - confirmHeight
        confirmBtn.layoutParams = FrameLayout.LayoutParams(confirmWidth, confirmHeight).apply {
            leftMargin = confirmLeft.coerceAtLeast(0)
            topMargin = confirmTop.coerceAtLeast(0)
        }
        confirmBtn.visibility = View.VISIBLE
    }

    private fun resetCropRect() {
        if (width == 0 || height == 0) return
        val w = (width * 0.75f).toInt()
        val h = (height * 0.22f).toInt()
        val left = (width - w) / 2
        val top = (height - h) / 2
        cropRect.set(left, top, left + w, top + h)
        updateButtonPositions()
        invalidate()
    }

    fun setInitialRect(rect: Rect) {
        cropRect.set(rect)
        updateButtonPositions()
        invalidate()
    }

    private fun saveCropRect() {
        prefs.edit().putString("crop_rect", "${cropRect.left},${cropRect.top},${cropRect.right},${cropRect.bottom}").apply()
    }

    private fun loadCropRect() {
        val str = prefs.getString("crop_rect", null) ?: return
        val parts = str.split(",")
        if (parts.size == 4) {
            val r = Rect(parts[0].toInt(), parts[1].toInt(), parts[2].toInt(), parts[3].toInt())
            if (r.left >= 0 && r.top >= 0 && r.right <= width && r.bottom <= height && r.width() > 0 && r.height() > 0) {
                cropRect.set(r)
            }
        }
    }

    private fun hasSelection(): Boolean {
        return cropRect.width() > 0 && cropRect.height() > 0
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!hasSelection()) {
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 48f
                textAlign = Paint.Align.CENTER
                setShadowLayer(8f, 0f, 4f, Color.BLACK)
            }
            canvas.drawText("拖动选择题目区域", width / 2f, height / 2f - 50, textPaint)
            canvas.drawText("松开后可拖动边角调整", width / 2f, height / 2f + 10, textPaint)
            return
        }

        val r = cropRect
        canvas.drawRect(r, borderPaint)

        val cl = dpToPx(18)
        canvas.drawLine(r.left.toFloat(), r.top.toFloat(), (r.left + cl).toFloat(), r.top.toFloat(), cornerPaint)
        canvas.drawLine(r.left.toFloat(), r.top.toFloat(), r.left.toFloat(), (r.top + cl).toFloat(), cornerPaint)
        canvas.drawLine((r.right - cl).toFloat(), r.top.toFloat(), r.right.toFloat(), r.top.toFloat(), cornerPaint)
        canvas.drawLine(r.right.toFloat(), r.top.toFloat(), r.right.toFloat(), (r.top + cl).toFloat(), cornerPaint)
        canvas.drawLine(r.left.toFloat(), (r.bottom - cl).toFloat(), r.left.toFloat(), r.bottom.toFloat(), cornerPaint)
        canvas.drawLine(r.left.toFloat(), r.bottom.toFloat(), (r.left + cl).toFloat(), r.bottom.toFloat(), cornerPaint)
        canvas.drawLine((r.right - cl).toFloat(), r.bottom.toFloat(), r.right.toFloat(), r.bottom.toFloat(), cornerPaint)
        canvas.drawLine(r.right.toFloat(), (r.bottom - cl).toFloat(), r.right.toFloat(), r.bottom.toFloat(), cornerPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x.toInt()
        val y = event.y.toInt()

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (isPointInButton(x, y)) {
                    return false
                }
                if (isInResizeHandleArea(x, y)) {
                    touchMode = TouchMode.RESIZE
                    lastX = event.x
                    lastY = event.y
                    return true
                }
                if (cropRect.contains(x, y)) {
                    touchMode = TouchMode.DRAG
                    lastX = event.x
                    lastY = event.y
                    return true
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                when (touchMode) {
                    TouchMode.DRAG -> {
                        val dx = (event.x - lastX).toInt()
                        val dy = (event.y - lastY).toInt()
                        var newLeft = cropRect.left + dx
                        var newTop = cropRect.top + dy
                        var newRight = newLeft + cropRect.width()
                        var newBottom = newTop + cropRect.height()

                        if (newLeft < 0) { newLeft = 0; newRight = cropRect.width() }
                        if (newTop < 0) { newTop = 0; newBottom = cropRect.height() }
                        if (newRight > width) { newRight = width; newLeft = width - cropRect.width() }
                        if (newBottom > height) { newBottom = height; newTop = height - cropRect.height() }

                        cropRect.set(newLeft, newTop, newRight, newBottom)
                        lastX = event.x
                        lastY = event.y
                        updateButtonPositions()
                        invalidate()
                    }
                    TouchMode.RESIZE -> {
                        cropRect.right = event.x.toInt().coerceIn(cropRect.left + minCropSize, width)
                        cropRect.bottom = event.y.toInt().coerceIn(cropRect.top + minCropSize, height)
                        updateButtonPositions()
                        invalidate()
                    }
                    else -> {}
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touchMode = TouchMode.NONE
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun isPointInButton(x: Int, y: Int): Boolean {
        val closeRect = Rect(cropRect.right - btnSize, cropRect.top, cropRect.right, cropRect.top + btnSize)
        val resetRect = Rect(cropRect.left, cropRect.top, cropRect.left + btnSize, cropRect.top + btnSize)
        val hideRect = Rect(cropRect.left, cropRect.bottom - btnSize, cropRect.left + btnSize, cropRect.bottom)
        val confirmRect = Rect(
            (cropRect.left + cropRect.right) / 2 - confirmWidth / 2,
            cropRect.bottom - confirmHeight,
            (cropRect.left + cropRect.right) / 2 + confirmWidth / 2,
            cropRect.bottom
        )
        return closeRect.contains(x, y) || resetRect.contains(x, y) ||
                hideRect.contains(x, y) || confirmRect.contains(x, y)
    }

    private fun isInResizeHandleArea(x: Int, y: Int): Boolean {
        val dx = x - cropRect.right
        val dy = y - cropRect.bottom
        val threshold = (handleRadius + dpToPx(12)).toInt()
        return dx * dx + dy * dy <= threshold * threshold
    }

    private inner class ResizeHandleTouchListener : View.OnTouchListener {
        private var startX = 0f
        private var startY = 0f
        private var startRight = 0
        private var startBottom = 0

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    startRight = cropRect.right
                    startBottom = cropRect.bottom
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startX
                    val dy = event.rawY - startY
                    cropRect.right = (startRight + dx.toInt()).coerceIn(cropRect.left + minCropSize, width)
                    cropRect.bottom = (startBottom + dy.toInt()).coerceIn(cropRect.top + minCropSize, height)
                    updateButtonPositions()
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    return true
                }
            }
            return false
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}