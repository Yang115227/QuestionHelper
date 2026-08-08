package com.questionhelper.search

import android.content.Context
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.questionhelper.R

class CropOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var onCropConfirmed: ((Rect) -> Unit)? = null
    var onCropCanceled: (() -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2196F3")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val overlayPaint = Paint().apply {
        color = Color.parseColor("#CC000000")
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2196F3")
        style = Paint.Style.FILL
    }

    private var startX = 0f
    private var startY = 0f
    private var endX = 0f
    private var endY = 0f
    private var isDragging = false
    private var activeHandle: Handle? = null
    private val handleSize = 30f

    private enum class Handle { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        setupButtons()
    }

    private fun setupButtons() {
        val buttonLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                bottomMargin = 100
            }
        }

        val confirmBtn = Button(context).apply {
            text = "确认搜题"
            setOnClickListener {
                if (hasSelection()) {
                    onCropConfirmed?.invoke(getSelectionRect())
                }
            }
        }

        val cancelBtn = Button(context).apply {
            text = "取消"
            setOnClickListener {
                onCropCanceled?.invoke()
            }
        }

        buttonLayout.addView(cancelBtn)
        buttonLayout.addView(confirmBtn)
        addView(buttonLayout)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!hasSelection()) return

        val rect = getSelectionRect()

        // 绘制暗色遮罩（除选中区域外）
        canvas.drawRect(0f, 0f, width.toFloat(), rect.top.toFloat(), overlayPaint)
        canvas.drawRect(0f, rect.top.toFloat(), rect.left.toFloat(), rect.bottom.toFloat(), overlayPaint)
        canvas.drawRect(rect.right.toFloat(), rect.top.toFloat(), width.toFloat(), rect.bottom.toFloat(), overlayPaint)
        canvas.drawRect(0f, rect.bottom.toFloat(), width.toFloat(), height.toFloat(), overlayPaint)

        // 绘制选中框
        canvas.drawRect(rect, paint)

        // 绘制四个角的手柄
        drawHandle(canvas, rect.left.toFloat(), rect.top.toFloat())
        drawHandle(canvas, rect.right.toFloat(), rect.top.toFloat())
        drawHandle(canvas, rect.left.toFloat(), rect.bottom.toFloat())
        drawHandle(canvas, rect.right.toFloat(), rect.bottom.toFloat())
    }

    private fun drawHandle(canvas: Canvas, x: Float, y: Float) {
        canvas.drawCircle(x, y, handleSize, handlePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                activeHandle = getHandleAt(event.x, event.y)
                if (activeHandle == null && !hasSelection()) {
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
                if (isDragging) {
                    when (activeHandle) {
                        Handle.TOP_LEFT -> { startX = event.x; startY = event.y }
                        Handle.TOP_RIGHT -> { endX = event.x; startY = event.y }
                        Handle.BOTTOM_LEFT -> { startX = event.x; endY = event.y }
                        Handle.BOTTOM_RIGHT -> { endX = event.x; endY = event.y }
                        Handle.CENTER -> {
                            val dx = event.x - startX
                            val dy = event.y - startY
                            startX += dx
                            startY += dy
                            endX += dx
                            endY += dy
                            startX = event.x
                            startY = event.y
                        }
                        null -> { endX = event.x; endY = event.y }
                    }
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                isDragging = false
                activeHandle = null
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun getHandleAt(x: Float, y: Float): Handle? {
        if (!hasSelection()) return null
        val rect = getSelectionRect()
        val handleRadius = handleSize * 2

        return when {
            dist(x, y, rect.left.toFloat(), rect.top.toFloat()) < handleRadius -> Handle.TOP_LEFT
            dist(x, y, rect.right.toFloat(), rect.top.toFloat()) < handleRadius -> Handle.TOP_RIGHT
            dist(x, y, rect.left.toFloat(), rect.bottom.toFloat()) < handleRadius -> Handle.BOTTOM_LEFT
            dist(x, y, rect.right.toFloat(), rect.bottom.toFloat()) < handleRadius -> Handle.BOTTOM_RIGHT
            rect.contains(x.toInt(), y.toInt()) -> Handle.CENTER
            else -> null
        }
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return kotlin.math.hypot(x1 - x2, y1 - y2)
    }

    private fun hasSelection(): Boolean {
        return kotlin.math.abs(endX - startX) > 20 && kotlin.math.abs(endY - startY) > 20
    }

    private fun getSelectionRect(): Rect {
        val left = kotlin.math.min(startX, endX).toInt().coerceIn(0, width)
        val top = kotlin.math.min(startY, endY).toInt().coerceIn(0, height)
        val right = kotlin.math.max(startX, endX).toInt().coerceIn(0, width)
        val bottom = kotlin.math.max(startY, endY).toInt().coerceIn(0, height)
        return Rect(left, top, right, bottom)
    }
}
