package com.questionhelper.search

import android.content.Context
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

    // 绘制相关
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
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }

    // 选区框
    private var cropRect = Rect()
    private val minCropSize = dpToPx(80)
    private val handleRadius = dpToPx(18).toFloat()

    // 触摸状态
    private enum class TouchMode { NONE, DRAG, RESIZE }
    private var touchMode = TouchMode.NONE
    private var lastX = 0f
    private var lastY = 0f

    init {
        setWillNotDraw(false)
        setupUI()
    }

    private fun setupUI() {
        // 顶部工具栏
        val topBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#CC000000"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dpToPx(52)
            ).apply {
                gravity = Gravity.TOP
                topMargin = dpToPx(32)
            }

            // 回到选框按钮（重置选区）
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

        // 确认搜题按钮（底部中央）
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
                    onCropConfirmed?.invoke(cropRect)
                }
            }
        }
        addView(confirmBtn)

        // 隐藏按钮（左下角，回到悬浮球）
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

        // 初始化选区框位置（布局完成后）
        post {
            resetCropRect()
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val r = cropRect

        // 四块遮罩（挖空中间）
        canvas.drawRect(0f, 0f, width.toFloat(), r.top.toFloat(), overlayPaint)
        canvas.drawRect(0f, r.top.toFloat(), r.left.toFloat(), r.bottom.toFloat(), overlayPaint)
        canvas.drawRect(r.right.toFloat(), r.top.toFloat(), width.toFloat(), r.bottom.toFloat(), overlayPaint)
        canvas.drawRect(0f, r.bottom.toFloat(), width.toFloat(), height.toFloat(), overlayPaint)

        // 蓝色边框
        canvas.drawRect(r, borderPaint)

        // 四角 L 形装饰线
        val cl = dpToPx(18)
        // 左上
        canvas.drawLine(r.left.toFloat(), r.top.toFloat(), (r.left + cl).toFloat(), r.top.toFloat(), cornerPaint)
        canvas.drawLine(r.left.toFloat(), r.top.toFloat(), r.left.toFloat(), (r.top + cl).toFloat(), cornerPaint)
        // 右上
        canvas.drawLine((r.right - cl).toFloat(), r.top.toFloat(), r.right.toFloat(), r.top.toFloat(), cornerPaint)
        canvas.drawLine(r.right.toFloat(), r.top.toFloat(), r.right.toFloat(), (r.top + cl).toFloat(), cornerPaint)
        // 左下
        canvas.drawLine(r.left.toFloat(), (r.bottom - cl).toFloat(), r.left.toFloat(), r.bottom.toFloat(), cornerPaint)
        canvas.drawLine(r.left.toFloat(), r.bottom.toFloat(), (r.left + cl).toFloat(), r.bottom.toFloat(), cornerPaint)
        // 右下
        canvas.drawLine((r.right - cl).toFloat(), r.bottom.toFloat(), r.right.toFloat(), r.bottom.toFloat(), cornerPaint)
        canvas.drawLine(r.right.toFloat(), (r.bottom - cl).toFloat(), r.right.toFloat(), r.bottom.toFloat(), cornerPaint)

        // 右下角缩放手柄（圆形背景 + 双向箭头）
        val hx = r.right.toFloat()
        val hy = r.bottom.toFloat()
        canvas.drawCircle(hx, hy, handleRadius, handlePaint)
        canvas.drawCircle(hx, hy, handleRadius, handleStrokePaint)

        // 画双向箭头（右下方向）
        val offset = 6f
        canvas.drawLine(hx - offset, hy + offset, hx + 4, hy + 14, arrowPaint)
        canvas.drawLine(hx + 4, hy + 14, hx + 14, hy + 4, arrowPaint)
        canvas.drawLine(hx + 14, hy + 4, hx + 14, hy + 10, arrowPaint)
        canvas.drawLine(hx + 14, hy + 4, hx + 8, hy + 4, arrowPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x.toInt()
        val y = event.y.toInt()

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // 优先检查缩放手柄
                if (isInResizeHandle(x, y)) {
                    touchMode = TouchMode.RESIZE
                    lastX = event.x
                    lastY = event.y
                    parent.requestDisallowInterceptTouchEvent(true)
                    return true
                }

                // 检查是否在选区框内
                if (cropRect.contains(x, y)) {
                    touchMode = TouchMode.DRAG
                    lastX = event.x
                    lastY = event.y
                    parent.requestDisallowInterceptTouchEvent(true)
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

                        // 边界限制
                        if (newLeft < 0) {
                            newLeft = 0; newRight = cropRect.width()
                        }
                        if (newTop < 0) {
                            newTop = 0; newBottom = cropRect.height()
                        }
                        if (newRight > width) {
                            newRight = width; newLeft = width - cropRect.width()
                        }
                        if (newBottom > height) {
                            newBottom = height; newTop = height - cropRect.height()
                        }

                        cropRect.set(newLeft, newTop, newRight, newBottom)
                        lastX = event.x
                        lastY = event.y
                        invalidate()
                    }
                    TouchMode.RESIZE -> {
                        val newRight = event.x.toInt().coerceIn(
                            cropRect.left + minCropSize,
                            width
                        )
                        val newBottom = event.y.toInt().coerceIn(
                            cropRect.top + minCropSize,
                            height
                        )
                        cropRect.right = newRight
                        cropRect.bottom = newBottom
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