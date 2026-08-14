package com.questionhelper.search

import android.content.Context
import android.content.SharedPreferences
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.widget.*
import androidx.core.content.ContextCompat

class CropOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var onCropConfirmed: ((Rect) -> Unit)? = null
    var onCropCanceled: (() -> Unit)? = null

    private val prefs: SharedPreferences = context.getSharedPreferences("crop_prefs", Context.MODE_PRIVATE)

    // 绘制
    private val overlayPaint = Paint().apply { color = Color.parseColor("#B3000000") }
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
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2196F3")
        style = Paint.Style.FILL
    }
    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    // 选区框
    private var cropRect = Rect()
    private val minCropSize = dpToPx(80)
    private val handleRadius = dpToPx(18).toFloat()

    // 触摸
    private enum class TouchMode { NONE, DRAG, RESIZE }
    private var touchMode = TouchMode.NONE
    private var lastX = 0f
    private var lastY = 0f

    init {
        setWillNotDraw(false)
        // 添加半透明背景，让用户明确知道选区层已显示
        setBackgroundColor(Color.parseColor("#40000000"))
        setupUI()
    }

    private fun setupUI() {
        // 顶部工具栏（嵌入到遮罩顶部）
        val topBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#CC000000"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dpToPx(52)
            ).apply {
                gravity = Gravity.TOP
            }

            // 回到选框按钮
            val backBtn = ImageButton(context).apply {
                setImageResource(android.R.drawable.ic_menu_revert)
                setBackgroundColor(Color.TRANSPARENT)
                setColorFilter(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(dpToPx(44), dpToPx(44)).apply {
                    leftMargin = dpToPx(8)
                }
                setOnClickListener { resetCropRect() }
            }
            addView(backBtn)

            // 拖拽提示
            val hint = TextView(context).apply {
                text = "按住此区域拖拽"
                setTextColor(Color.WHITE)
                textSize = 15f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f
                )
            }
            addView(hint)

            // 关闭按钮
            val closeBtn = ImageButton(context).apply {
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                setBackgroundColor(Color.TRANSPARENT)
                setColorFilter(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(dpToPx(44), dpToPx(44)).apply {
                    rightMargin = dpToPx(8)
                }
                setOnClickListener { onCropCanceled?.invoke() }
            }
            addView(closeBtn)
        }
        addView(topBar)

        // 确认搜题按钮（底部中央，嵌入在遮罩底部）
        val confirmBtn = Button(context).apply {
            text = "确认搜题"
            setTextColor(Color.WHITE)
            textSize = 16f
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(24).toFloat()
                setColor(Color.parseColor("#2196F3"))
            }
            layoutParams = FrameLayout.LayoutParams(dpToPx(160), dpToPx(52)).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = dpToPx(100)
            }
            setOnClickListener {
                if (cropRect.width() >= minCropSize && cropRect.height() >= minCropSize) {
                    // 关键修复：立即给用户视觉反馈
                    this.text = "正在识别..."
                    this.isEnabled = false
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dpToPx(24).toFloat()
                        setColor(Color.parseColor("#757575"))
                    }
                    saveCropRect()
                    onCropConfirmed?.invoke(cropRect)
                }
            }
        }
        addView(confirmBtn)

        // 隐藏按钮（左下角）
        val hideBtn = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_menu_view)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(dpToPx(48), dpToPx(48)).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                bottomMargin = dpToPx(24)
                leftMargin = dpToPx(24)
            }
            setOnClickListener { onCropCanceled?.invoke() }
        }
        addView(hideBtn)

        // 初始化选区（先默认，布局完成后再加载记忆）
        post {
            loadCropRect()
            if (cropRect.isEmpty) {
                resetCropRect()
            }
        }
    }

    private fun resetCropRect() {
        if (width == 0 || height == 0) return
        val w = (width * 0.75f).toInt()
        val h = (height * 0.22f).toInt()
        val left = (width - w) / 2
        val top = (height - h) / 2
        cropRect.set(left, top, left + w, top + h)
        invalidate()
    }

    fun setInitialRect(rect: Rect) {
        cropRect.set(rect)
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
            // 校验边界
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
            // 初始状态：显示提示文字和虚线框
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 56f
                textAlign = Paint.Align.CENTER
                // 文字阴影，增强可读性
                setShadowLayer(10f, 0f, 4f, Color.BLACK)
            }
            canvas.drawText("👆 拖动选择题目区域", width / 2f, height / 2f - 100, textPaint)
            canvas.drawText("松开后可拖动边角调整", width / 2f, height / 2f - 20, textPaint)

            // 画一个虚线框提示可交互区域
            val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#80FFFFFF")
                style = Paint.Style.STROKE
                strokeWidth = 4f
                pathEffect = android.graphics.DashPathEffect(floatArrayOf(20f, 20f), 0f)
            }
            val hintRect = RectF(
                width * 0.15f, height * 0.3f,
                width * 0.85f, height * 0.6f
            )
            canvas.drawRoundRect(hintRect, 20f, 20f, hintPaint)
            return
        }

        val r = cropRect

        // 四块遮罩（挖空中间选区）
        canvas.drawRect(0f, 0f, width.toFloat(), r.top.toFloat(), overlayPaint)
        canvas.drawRect(0f, r.top.toFloat(), r.left.toFloat(), r.bottom.toFloat(), overlayPaint)
        canvas.drawRect(r.right.toFloat(), r.top.toFloat(), width.toFloat(), r.bottom.toFloat(), overlayPaint)
        canvas.drawRect(0f, r.bottom.toFloat(), width.toFloat(), height.toFloat(), overlayPaint)

        // 蓝色边框
        canvas.drawRect(r, borderPaint)

        // 四角 L 形装饰线
        val cl = dpToPx(18)
        canvas.drawLine(r.left.toFloat(), r.top.toFloat(), (r.left + cl).toFloat(), r.top.toFloat(), cornerPaint)
        canvas.drawLine(r.left.toFloat(), r.top.toFloat(), r.left.toFloat(), (r.top + cl).toFloat(), cornerPaint)
        canvas.drawLine((r.right - cl).toFloat(), r.top.toFloat(), r.right.toFloat(), r.top.toFloat(), cornerPaint)
        canvas.drawLine(r.right.toFloat(), r.top.toFloat(), r.right.toFloat(), (r.top + cl).toFloat(), cornerPaint)
        canvas.drawLine(r.left.toFloat(), (r.bottom - cl).toFloat(), r.left.toFloat(), r.bottom.toFloat(), cornerPaint)
        canvas.drawLine(r.left.toFloat(), r.bottom.toFloat(), (r.left + cl).toFloat(), r.bottom.toFloat(), cornerPaint)
        canvas.drawLine((r.right - cl).toFloat(), r.bottom.toFloat(), r.right.toFloat(), r.bottom.toFloat(), cornerPaint)
        canvas.drawLine(r.right.toFloat(), (r.bottom - cl).toFloat(), r.right.toFloat(), r.bottom.toFloat(), cornerPaint)

        // 右下角缩放手柄
        val hx = r.right.toFloat()
        val hy = r.bottom.toFloat()
        canvas.drawCircle(hx, hy, handleRadius, handlePaint)
        canvas.drawCircle(hx, hy, handleRadius, handleStrokePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x.toInt()
        val y = event.y.toInt()

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (isInResizeHandle(x, y)) {
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
                        invalidate()
                    }
                    TouchMode.RESIZE -> {
                        cropRect.right = event.x.toInt().coerceIn(cropRect.left + minCropSize, width)
                        cropRect.bottom = event.y.toInt().coerceIn(cropRect.top + minCropSize, height)
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

    private fun isInResizeHandle(x: Int, y: Int): Boolean {
        val dx = x - cropRect.right
        val dy = y - cropRect.bottom
        val threshold = (handleRadius + dpToPx(12)).toInt()
        return dx * dx + dy * dy <= threshold * threshold
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
