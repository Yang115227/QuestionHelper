package com.questionhelper.search

import android.content.Context
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.*
import android.widget.*
import com.questionhelper.R

class FloatResultView(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var container: FrameLayout? = null
    private var isShowing = false

    // 用于记忆位置的 SharedPreferences
    private val prefs: SharedPreferences =
        context.getSharedPreferences("float_result_prefs", Context.MODE_PRIVATE)

    var onDismiss: (() -> Unit)? = null

    companion object {
        private const val TAG = "FloatResultView"
        private const val AUTO_DISMISS_DELAY = 30000L
        private const val MIN_WIDTH_DP = 280
        private const val MIN_HEIGHT_DP = 160
        private const val MAX_WIDTH_DP = 500
        private const val MAX_HEIGHT_DP = 600
        private const val KEY_LAST_X = "last_x"
        private const val KEY_LAST_Y = "last_y"
    }

    fun show(question: String, answer: String, analysis: String, isMatched: Boolean) {
        if (isShowing) dismiss()

        val root = FrameLayout(context).apply {
            setBackgroundColor(0x00000000)
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(12).toFloat()
                setColor(0xCC000000.toInt()) // 半透明黑色
            }
        }

        val titleBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(36)
            )
        }

        val dragHint = TextView(context).apply {
            text = "🔍 搜题结果（按住此处拖拽）"
            textSize = 11f
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val closeBtn = TextView(context).apply {
            text = "✕"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dpToPx(28), dpToPx(28))
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(0x33FFFFFF)
            }
            setOnClickListener { dismiss() }
        }

        titleBar.addView(dragHint)
        titleBar.addView(closeBtn)
        card.addView(titleBar)

        card.addView(createTransparentDivider())

        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
        }

        contentLayout.addView(createLabel("题目", 12f, 0xFFFFFFFF.toInt()))
        contentLayout.addView(createContentText(question, 13f, 0xFFFFFFFF.toInt()))
        contentLayout.addView(createSpacer(6))

        contentLayout.addView(createLabel(if (isMatched) "答案 ✅ 已匹配题库" else "答案 ⚠️ 未匹配题库", 12f, 0xFFFFFFFF.toInt()))
        val answerTextView = createContentText("", 14f, 0xFFFFFFFF.toInt(), true)
        if (isMatched && answer.contains("【正确】")) {
            answerTextView.text = createAnswerSpannable(answer)
        } else {
            answerTextView.text = answer
            answerTextView.setTextColor(0xFFFFFFFF.toInt())
        }
        contentLayout.addView(answerTextView)
        contentLayout.addView(createSpacer(6))

        if (analysis.isNotBlank()) {
            contentLayout.addView(createLabel("解析", 12f, 0xFFFFFFFF.toInt()))
            contentLayout.addView(createContentText(analysis, 12f, 0xFFFFFFFF.toInt()))
        }

        if (!isMatched) {
            contentLayout.addView(createSpacer(4))
            contentLayout.addView(createContentText(
                "提示：未在题库中找到完全匹配的题目，以上为 OCR 识别结果。",
                10f, 0x88FFFFFF.toInt()
            ))
        }

        card.addView(contentLayout)
        root.addView(card)

        val resizeHandle = TextView(context).apply {
            text = "◢"
            textSize = 18f
            setTextColor(0xCCFFFFFF.toInt())
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(dpToPx(28), dpToPx(28)).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                bottomMargin = dpToPx(4)
                rightMargin = dpToPx(4)
            }
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(0x33FFFFFF)
            }
        }
        root.addView(resizeHandle)

        val screenWidth = context.resources.displayMetrics.widthPixels
        val maxWidth = dpToPx(MAX_WIDTH_DP)
        val minWidth = dpToPx(MIN_WIDTH_DP)
        val initialWidth = (screenWidth * 0.85f).toInt().coerceIn(minWidth, maxWidth)

        // 读取上次保存的位置
        val savedX = prefs.getInt(KEY_LAST_X, dpToPx(20))
        val savedY = prefs.getInt(KEY_LAST_Y, dpToPx(120))

        val params = WindowManager.LayoutParams(
            initialWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX
            y = savedY
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

    private fun createAnswerSpannable(answer: String): SpannableStringBuilder {
        val builder = SpannableStringBuilder()
        val lines = answer.split("\n")
        for (line in lines) {
            val start = builder.length
            if (line.startsWith("【正确】")) {
                val cleanLine = line.removePrefix("【正确】")
                builder.append(cleanLine)
                builder.setSpan(
                    ForegroundColorSpan(0xFF00C853.toInt()),
                    start,
                    builder.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            } else {
                builder.append(line)
            }
            if (line != lines.last()) {
                builder.append("\n")
            }
        }
        return builder
    }

    private fun createTransparentDivider(): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(1)
            ).apply { topMargin = dpToPx(6); bottomMargin = dpToPx(6) }
            setBackgroundColor(0x33FFFFFF)
        }
    }

    private fun createLabel(text: String, size: Float, color: Int): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(3) }
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

    private fun createSpacer(heightDp: Int): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(heightDp)
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
                MotionEvent.ACTION_UP -> {
                    // 保存拖拽后的位置
                    prefs.edit()
                        .putInt(KEY_LAST_X, params.x)
                        .putInt(KEY_LAST_Y, params.y)
                        .apply()
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
                    val newHeight = (startHeight + dy.toInt()).coerceAtLeast(dpToPx(MIN_HEIGHT_DP))
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