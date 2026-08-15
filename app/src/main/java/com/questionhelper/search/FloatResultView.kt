package com.questionhelper.search

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.widget.*
import com.questionhelper.R

class FloatResultView(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var container: FrameLayout? = null
    private var isShowing = false

    var onDismiss: (() -> Unit)? = null

    companion object {
        private const val TAG = "FloatResultView"
        private const val AUTO_DISMISS_DELAY = 30000L
        private const val MIN_WIDTH_DP = 280
        private const val MIN_HEIGHT_DP = 200
        private const val MAX_WIDTH_DP = 500
        private const val MAX_HEIGHT_DP = 600
    }

    fun show(question: String, answer: String, analysis: String, isMatched: Boolean) {
        if (isShowing) dismiss()

        val root = FrameLayout(context).apply {
            setBackgroundColor(0x00000000)
        }

        // 内容卡片：半透明黑色背景，提升可读性
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(16).toFloat()
                setColor(0xCC000000.toInt()) // 半透明黑色 (80%不透明)
            }
        }

        val titleBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(44)
            )
        }

        val dragHint = TextView(context).apply {
            text = "🔍 搜题结果（按住此处拖拽）"
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val closeBtn = TextView(context).apply {
            text = "✕"
            textSize = 20f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dpToPx(36), dpToPx(36))
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(0x33FFFFFF) // 半透明白色背景
            }
            setOnClickListener { dismiss() }
        }

        titleBar.addView(dragHint)
        titleBar.addView(closeBtn)
        card.addView(titleBar)

        card.addView(createTransparentDivider())

        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(320)
            )
            setBackgroundColor(0x00000000) // 透明
        }

        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
        }

        contentLayout.addView(createLabel("题目", 0xFFFFFFFF.toInt()))
        contentLayout.addView(createContentText(question, 15f, 0xFFFFFFFF.toInt()))
        contentLayout.addView(createSpacer())

        val answerColor = if (isMatched) 0xFF00C853.toInt() else 0xFFFFFFFF.toInt()
        val answerLabel = if (isMatched) "答案 ✅ 已匹配题库" else "答案 ⚠️ 未匹配题库"
        contentLayout.addView(createLabel(answerLabel, 0xFFFFFFFF.toInt()))
        contentLayout.addView(createContentText(answer, 17f, answerColor, true))
        contentLayout.addView(createSpacer())

        if (analysis.isNotBlank()) {
            contentLayout.addView(createLabel("解析", 0xFFFFFFFF.toInt()))
            contentLayout.addView(createContentText(analysis, 14f, 0xFFFFFFFF.toInt()))
        }

        if (!isMatched) {
            contentLayout.addView(createSpacer())
            contentLayout.addView(createContentText(
                "提示：未在题库中找到完全匹配的题目，以上为 OCR 识别结果。",
                12f, 0x88FFFFFF.toInt()
            ))
        }

        scrollView.addView(contentLayout)
        card.addView(scrollView)
        root.addView(card)

        // 右下角半透明缩放手柄
        val resizeHandle = TextView(context).apply {
            text = "◢"
            textSize = 24f
            setTextColor(0xCCFFFFFF.toInt())
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(dpToPx(36), dpToPx(36)).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                bottomMargin = dpToPx(8)
                rightMargin = dpToPx(8)
            }
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(0x33FFFFFF) // 半透明白色背景
            }
        }
        root.addView(resizeHandle)

        val screenWidth = context.resources.displayMetrics.widthPixels
        val maxWidth = dpToPx(MAX_WIDTH_DP)
        val minWidth = dpToPx(MIN_WIDTH_DP)
        val initialWidth = (screenWidth * 0.85f).toInt().coerceIn(minWidth, maxWidth)

        val params = WindowManager.LayoutParams(
            initialWidth,
            dpToPx(320) + dpToPx(80),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dpToPx(20)
            y = dpToPx(120)
        }

        titleBar.setOnTouchListener(DragTouchListener(params, root))
        resizeHandle.setOnTouchListener(ResizeTouchListener(params, root))

        container = root
        try {
            windowManager.addView(root, params)
            isShowing = true
            Log.d(TAG, "Result window shown, matched=$isMatched")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show result window", e)
            onDismiss?.invoke()
            return
        }

        Handler(Looper.getMainLooper()).postDelayed({
            if (isShowing) dismiss()
        }, AUTO_DISMISS_DELAY)
    }

    fun dismiss() {
        container?.let {
            try { windowManager.removeView(it) } catch (e: Exception) { Log.e(TAG, "Dismiss failed", e) }
            container = null
            isShowing = false
        }
        onDismiss?.invoke()
    }

    fun isShowing(): Boolean = isShowing

    private fun createTransparentDivider(): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(1)
            ).apply { topMargin = dpToPx(8); bottomMargin = dpToPx(8) }
            setBackgroundColor(0x33FFFFFF)
        }
    }

    private fun createLabel(text: String, color: Int): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 12f
            setTextColor(color)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(4) }
        }
    }

    private fun createContentText(text: String, size: Float, color: Int, isBold: Boolean = false): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (isBold) paint.isFakeBoldText = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun createSpacer(): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(12)
            )
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt()

    private inner class DragTouchListener(
        private val params: WindowManager.LayoutParams,
        private val view: View
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
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    windowManager.updateViewLayout(view, params)
                    return true
                }
            }
            return false
        }
    }

    private inner class ResizeTouchListener(
        private val params: WindowManager.LayoutParams,
        private val view: View
    ) : View.OnTouchListener {
        private var startX = 0f
        private var startY = 0f
        private var startWidth = 0
        private var startHeight = 0

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    startWidth = params.width
                    startHeight = params.height
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startX
                    val dy = event.rawY - startY
                    val newWidth = (startWidth + dx.toInt()).coerceIn(dpToPx(MIN_WIDTH_DP), dpToPx(MAX_WIDTH_DP))
                    val newHeight = (startHeight + dy.toInt()).coerceIn(dpToPx(MIN_HEIGHT_DP), dpToPx(MAX_HEIGHT_DP))
                    params.width = newWidth
                    params.height = newHeight
                    windowManager.updateViewLayout(view, params)
                    return true
                }
            }
            return false
        }
    }
}