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

    // 选区框边框：红色透明
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80FF0000") // 半透明红色
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80FF0000")
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private var cropRect = Rect()
    private val minCropSize = dpToPx(80)

    private enum class TouchMode { NONE, DRAG }
    private var touchMode = TouchMode.NONE
    private var lastX = 0f
    private var lastY = 0f

    // 按钮
    private lateinit var closeBtn: ImageButton
    private lateinit var hideBtn: ImageButton
    private lateinit var confirmBtn: Button
    private lateinit var upBtn: TextView
    private lateinit var downBtn: TextView
    private lateinit var leftBtn: TextView
    private lateinit var rightBtn: TextView

    private val btnSize = dpToPx(36)
    private val directionBtnSize = dpToPx(28)
    private val confirmWidth = dpToPx(100)
    private val confirmHeight = dpToPx(40)

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
        // 关闭按钮（右上角）
        closeBtn = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            background = createCircleButtonBackground()
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setOnClickListener { onCropCanceled?.invoke() }
        }

        // 隐藏按钮（左下角）
        hideBtn = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_menu_view)
            background = createCircleButtonBackground()
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setOnClickListener { onCropCanceled?.invoke() }
        }

        // 确认按钮（底部靠左）
        confirmBtn = Button(context).apply {
            text = "确认搜题"
            setTextColor(Color.WHITE)
            textSize = 14f
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = confirmHeight / 2f
                setColor(Color.parseColor("#80FF0000"))
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

        // 四个方向键按钮（右下角 2x2 排列）
        upBtn = TextView(context).apply {
            text = "↑"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = createCircleButtonBackground()
            setOnTouchListener(DirectionTouchListener("up"))
        }
        downBtn = TextView(context).apply {
            text = "↓"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = createCircleButtonBackground()
            setOnTouchListener(DirectionTouchListener("down"))
        }
        leftBtn = TextView(context).apply {
            text = "←"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = createCircleButtonBackground()
            setOnTouchListener(DirectionTouchListener("left"))
        }
        rightBtn = TextView(context).apply {
            text = "→"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = createCircleButtonBackground()
            setOnTouchListener(DirectionTouchListener("right"))
        }

        addView(closeBtn)
        addView(hideBtn)
        addView(confirmBtn)
        addView(upBtn)
        addView(downBtn)
        addView(leftBtn)
        addView(rightBtn)
    }

    private fun createCircleButtonBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#80FF0000")) // 红色透明
        }
    }

    private fun updateButtonPositions() {
        if (!this::closeBtn.isInitialized) return
        val left = cropRect.left
        val top = cropRect.top
        val right = cropRect.right
        val bottom = cropRect.bottom

        // 关闭按钮：右上角内侧
        closeBtn.layoutParams = FrameLayout.LayoutParams(btnSize, btnSize).apply {
            leftMargin = right - btnSize
            topMargin = top
        }
        closeBtn.visibility = View.VISIBLE

        // 隐藏按钮：左下角内侧
        hideBtn.layoutParams = FrameLayout.LayoutParams(btnSize, btnSize).apply {
            leftMargin = left
            topMargin = bottom - btnSize
        }
        hideBtn.visibility = View.VISIBLE

        // 确认按钮：底部靠左
        confirmBtn.layoutParams = FrameLayout.LayoutParams(confirmWidth, confirmHeight).apply {
            leftMargin = left + dpToPx(8)
            topMargin = bottom - confirmHeight - dpToPx(8)
        }
        confirmBtn.visibility = View.VISIBLE

        // 四个方向键：右下角 2x2 排列
        val dirAreaRight = right - dpToPx(8)
        val dirAreaBottom = bottom - dpToPx(8)
        val half = directionBtnSize / 2

        upBtn.layoutParams = FrameLayout.LayoutParams(directionBtnSize, directionBtnSize).apply {
            leftMargin = dirAreaRight - directionBtnSize - half
            topMargin = dirAreaBottom - directionBtnSize * 2 + half
        }
        downBtn.layoutParams = FrameLayout.LayoutParams(directionBtnSize, directionBtnSize).apply {
            leftMargin = dirAreaRight - directionBtnSize - half
            topMargin = dirAreaBottom - directionBtnSize + half
        }
        leftBtn.layoutParams = FrameLayout.LayoutParams(directionBtnSize, directionBtnSize).apply {
            leftMargin = dirAreaRight - directionBtnSize * 2 - half
            topMargin = dirAreaBottom - directionBtnSize + half
        }
        rightBtn.layoutParams = FrameLayout.LayoutParams(directionBtnSize, directionBtnSize).apply {
            leftMargin = dirAreaRight - directionBtnSize + half
            topMargin = dirAreaBottom - directionBtnSize + half
        }
        upBtn.visibility = View.VISIBLE
        downBtn.visibility = View.VISIBLE
        leftBtn.visibility = View.VISIBLE
        rightBtn.visibility = View.VISIBLE
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
                if (isPointInAnyButton(x, y)) {
                    return false
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
                if (touchMode == TouchMode.DRAG) {
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
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touchMode = TouchMode.NONE
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun isPointInAnyButton(x: Int, y: Int): Boolean {
        val closeRect = Rect(cropRect.right - btnSize, cropRect.top, cropRect.right, cropRect.top + btnSize)
        val hideRect = Rect(cropRect.left, cropRect.bottom - btnSize, cropRect.left + btnSize, cropRect.bottom)
        val confirmRect = Rect(
            cropRect.left + dpToPx(8),
            cropRect.bottom - confirmHeight - dpToPx(8),
            cropRect.left + dpToPx(8) + confirmWidth,
            cropRect.bottom - dpToPx(8)
        )
        // 方向键区域（2x2 网格）
        val dirAreaRight = cropRect.right - dpToPx(8)
        val dirAreaBottom = cropRect.bottom - dpToPx(8)
        val half = directionBtnSize / 2
        val upRect = Rect(
            dirAreaRight - directionBtnSize - half, dirAreaBottom - directionBtnSize * 2 + half,
            dirAreaRight - half, dirAreaBottom - directionBtnSize + half
        )
        val downRect = Rect(
            dirAreaRight - directionBtnSize - half, dirAreaBottom - directionBtnSize + half,
            dirAreaRight - half, dirAreaBottom + half
        )
        val leftRect = Rect(
            dirAreaRight - directionBtnSize * 2 - half, dirAreaBottom - directionBtnSize + half,
            dirAreaRight - directionBtnSize - half, dirAreaBottom + half
        )
        val rightRect = Rect(
            dirAreaRight - directionBtnSize + half, dirAreaBottom - directionBtnSize + half,
            dirAreaRight + half, dirAreaBottom + half
        )
        return closeRect.contains(x, y) || hideRect.contains(x, y) ||
                confirmRect.contains(x, y) || upRect.contains(x, y) ||
                downRect.contains(x, y) || leftRect.contains(x, y) ||
                rightRect.contains(x, y)
    }

    private inner class DirectionTouchListener(private val direction: String) : View.OnTouchListener {
        private var startX = 0f
        private var startY = 0f
        private var startLeft = 0
        private var startTop = 0
        private var startRight = 0
        private var startBottom = 0

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    startLeft = cropRect.left
                    startTop = cropRect.top
                    startRight = cropRect.right
                    startBottom = cropRect.bottom
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startX
                    val dy = event.rawY - startY
                    when (direction) {
                        "up" -> {
                            cropRect.top = (startTop + dy.toInt())
                                .coerceIn(0, cropRect.bottom - minCropSize)
                        }
                        "down" -> {
                            cropRect.bottom = (startBottom + dy.toInt())
                                .coerceIn(cropRect.top + minCropSize, height)
                        }
                        "left" -> {
                            cropRect.left = (startLeft + dx.toInt())
                                .coerceIn(0, cropRect.right - minCropSize)
                        }
                        "right" -> {
                            cropRect.right = (startRight + dx.toInt())
                                .coerceIn(cropRect.left + minCropSize, width)
                        }
                    }
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