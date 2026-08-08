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
        color = Color.parseColor("#B3000000")
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

    private var startX = 0f
    private var startY = 0f
    private var endX = 0f
    private var endY = 0f
    private var isDragging = false
    private var activeHandle: Handle? = null
    private val handleRadius = 24f
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
                    bottomMargin = dpToPx(120)
                }
            }

            val cancelBtn = Button(context).apply {
                text = "取消"
                setOnClickListener { onCropCanceled?.invoke() }
                layoutParams = LinearLayout.LayoutParams(
                    dpToPx(100), LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dpToPx(16) }
            }

            val confirmBtn = Button(context).apply {
                text = "确认搜题"
                setOnClickListener {
                    if (hasSelection()) {
                        onCropConfirmed?.invoke(getSelectionRect())
                    }
                }
                layoutParams = LinearLayout.LayoutParams(
                    dpToPx(120), LinearLayout.LayoutParams.WRAP_CONTENT
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
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 48f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("拖动选择题目区域", width / 2f, height / 2f - 60, textPaint)
            canvas.drawText("松开后可调整边角", width / 2f, height / 2f, textPaint)
            return
        }

        val rect = getSelectionRect()

        // 暗色遮罩
        canvas.drawRect(0f, 0f, width.toFloat(), rect.top.toFloat(), overlayPaint)
        canvas.drawRect(0f, rect.top.toFloat(), rect.left.toFloat(), rect.bottom.toFloat(), overlayPaint)
        canvas.drawRect(rect.right.toFloat(), rect.top.toFloat(), width.toFloat(), rect.bottom.toFloat(), overlayPaint)
        canvas.drawRect(0f, rect.bottom.toFloat(), width.toFloat(), height.toFloat(), overlayPaint)

        // 选中框
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
                        startX = event.x
                        startY = event.y
                    }
                    Handle.TOP_RIGHT -> {
                        endX = event.x
                        startY = event.y
                    }
                    Handle.BOTTOM_LEFT -> {
                        startX = event.x
                        endY = event.y
                    }
                    Handle.BOTTOM_RIGHT -> {
                        endX = event.x
                        endY = event.y
                    }
                    Handle.CENTER -> {
                        val width = endX - startX
                        val height = endY - startY
                        startX = event.x - width / 2
                        startY = event.y - height / 2
                        endX = startX + width
                        endY = startY + height
                    }
                    else -> {
                        endX = event.x
                        endY = event.y
                    }
                }
                
                startX = startX.coerceIn(0f, width.toFloat())
                startY = startY.coerceIn(0f, height.toFloat())
                endX = endX.coerceIn(0f, width.toFloat())
                endY = endY.coerceIn(0f, height.toFloat())
                
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