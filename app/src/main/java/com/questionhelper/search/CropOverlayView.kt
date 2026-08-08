package com.questionhelper.search

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout

class CropOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var onCropConfirmed: ((Rect) -> Unit)? = null
    var onCropCanceled: (() -> Unit)? = null

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2196F3")
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    
    private val overlayPaint = Paint().apply {
        color = Color.parseColor("#CC000000")  // 半透明黑
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

    private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#66FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 56f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        setShadowLayer(8f, 0f, 0f, Color.BLACK)
    }

    private var startX = 0f
    private var startY = 0f
    private var endX = 0f
    private var endY = 0f
    private var isDragging = false
    private var activeHandle: Handle? = null
    private val handleRadius = 28f
    private val minSize = 60f

    private enum class Handle { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER, NONE }

    init {
        setWillNotDraw(false)
        setupButtons()
    }

    private fun setupButtons() {
        post {
            val buttonLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                    bottomMargin = dpToPx(140)
                }
            }

            val cancelBtn = Button(context).apply {
                text = "取消"
                setOnClickListener { onCropCanceled?.invoke() }
                layoutParams = LinearLayout.LayoutParams(
                    dpToPx(110), LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dpToPx(20) }
            }

            val confirmBtn = Button(context).apply {
                text = "确认搜题"
                setOnClickListener {
                    if (hasSelection()) {
                        onCropConfirmed?.invoke(getSelectionRect())
                    }
                }
                layoutParams = LinearLayout.LayoutParams(
                    dpToPx(130), LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            buttonLayout.addView(cancelBtn)
            buttonLayout.addView(confirmBtn)
            addView(buttonLayout)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (!hasSelection()) {
            // 初始状态：全屏遮罩 + 十字线 + 文字提示
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
            
            // 画十字辅助线
            val cx = width / 2f
            val cy = height / 2f
            canvas.drawLine(cx - 100, cy, cx + 100, cy, crossPaint)
            canvas.drawLine(cx, cy - 100, cx, cy + 100, crossPaint)
            
            canvas.drawText("👆 拖动选择题目区域", cx, cy - 100, textPaint)
            canvas.drawText("松开后可拖动边角调整", cx, cy - 30, textPaint)
            return
        }

        val rect = getSelectionRect()

        // 四块暗色遮罩（挖空中间选中区域）
        canvas.drawRect(0f, 0f, width.toFloat(), rect.top.toFloat(), overlayPaint)
        canvas.drawRect(0f, rect.top.toFloat(), rect.left.toFloat(), rect.bottom.toFloat(), overlayPaint)
        canvas.drawRect(rect.right.toFloat(), rect.top.toFloat(), width.toFloat(), rect.bottom.toFloat(), overlayPaint)
        canvas.drawRect(0f, rect.bottom.toFloat(), width.toFloat(), height.toFloat(), overlayPaint)

        // 选中框边框
        canvas.drawRect(rect, borderPaint)

        // 四个角手柄
        drawHandle(canvas, rect.left.toFloat(), rect.top.toFloat())
        drawHandle(canvas, rect.right.toFloat(), rect.top.toFloat())
        drawHandle(canvas, rect.left.toFloat(), rect.bottom.toFloat())
        drawHandle(canvas, rect.right.toFloat(), rect.bottom.toFloat())
    }

    private fun drawHandle(canvas: Canvas, x: Float, y: Float) {
        canvas.drawCircle(x, y, handleRadius, handlePaint)
        canvas.drawCircle(x, y, handleRadius, handleStrokePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeHandle = getHandleAt(event.x, event.y)
                if (activeHandle == Handle.NONE) {
                    startX = event.x
                    startY = event.y
                    endX = event.x
                    endY = event.y
                    isDragging = true
                } else if (activeHandle == Handle.CENTER) {
                    startX = event.x
                    startY = event.y
                    isDragging = true
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) return true
                
                when (activeHandle) {
                    Handle.TOP_LEFT -> {
                        startX = event.x.coerceIn(0f, endX - minSize)
                        startY = event.y.coerceIn(0f, endY - minSize)
                    }
                    Handle.TOP_RIGHT -> {
                        endX = event.x.coerceIn(startX + minSize, width.toFloat())
                        startY = event.y.coerceIn(0f, endY - minSize)
                    }
                    Handle.BOTTOM_LEFT -> {
                        startX = event.x.coerceIn(0f, endX - minSize)
                        endY = event.y.coerceIn(startY + minSize, height.toFloat())
                    }
                    Handle.BOTTOM_RIGHT -> {
                        endX = event.x.coerceIn(startX + minSize, width.toFloat())
                        endY = event.y.coerceIn(startY + minSize, height.toFloat())
                    }
                    Handle.CENTER -> {
                        val dx = event.x - startX
                        val dy = event.y - startY
                        val w = endX - startX
                        val h = endY - startY
                        
                        var newStartX = startX + dx
                        var newStartY = startY + dy
                        var newEndX = newStartX + w
                        var newEndY = newStartY + h
                        
                        // 边界限制
                        if (newStartX < 0) { newStartX = 0f; newEndX = w }
                        if (newStartY < 0) { newStartY = 0f; newEndY = h }
                        if (newEndX > width) { newEndX = width.toFloat(); newStartX = newEndX - w }
                        if (newEndY > height) { newEndY = height.toFloat(); newStartY = newEndY - h }
                        
                        startX = newStartX
                        startY = newStartY
                        endX = newEndX
                        endY = newEndY
                        startX = event.x
                        startY = event.y
                    }
                    else -> {
                        endX = event.x.coerceIn(0f, width.toFloat())
                        endY = event.y.coerceIn(0f, height.toFloat())
                    }
                }
                
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                isDragging = false
                activeHandle = Handle.NONE
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun getHandleAt(x: Float, y: Float): Handle {
        if (!hasSelection()) return Handle.NONE
        val rect = getSelectionRect()
        val threshold = handleRadius * 2.5f

        return when {
            dist(x, y, rect.left.toFloat(), rect.top.toFloat()) < threshold -> Handle.TOP_LEFT
            dist(x, y, rect.right.toFloat(), rect.top.toFloat()) < threshold -> Handle.TOP_RIGHT
            dist(x, y, rect.left.toFloat(), rect.bottom.toFloat()) < threshold -> Handle.BOTTOM_LEFT
            dist(x, y, rect.right.toFloat(), rect.bottom.toFloat()) < threshold -> Handle.BOTTOM_RIGHT
            rect.contains(x.toInt(), y.toInt()) -> Handle.CENTER
            else -> Handle.NONE
        }
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return kotlin.math.hypot(x1 - x2, y1 - y2)
    }

    private fun hasSelection(): Boolean {
        return kotlin.math.abs(endX - startX) > minSize && kotlin.math.abs(endY - startY) > minSize
    }

    private fun getSelectionRect(): Rect {
        val left = kotlin.math.min(startX, endX).toInt().coerceIn(0, width)
        val top = kotlin.math.min(startY, endY).toInt().coerceIn(0, height)
        val right = kotlin.math.max(startX, endX).toInt().coerceIn(0, width)
        val bottom = kotlin.math.max(startY, endY).toInt().coerceIn(0, height)
        return Rect(left, top, right, bottom)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}