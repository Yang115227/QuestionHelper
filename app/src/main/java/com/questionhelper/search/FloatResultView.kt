package com.questionhelper.search

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import com.questionhelper.R

/**
 * 悬浮搜索结果窗：支持拖拽、透明背景、正确结果标红
 */
class FloatResultView(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var container: FrameLayout? = null
    private var isShowing = false

    companion object {
        private const val TAG = "FloatResultView"
    }

    /**
     * 显示搜索结果悬浮窗
     * @param question OCR 识别到的题目文本
     * @param answer 答案文本
     * @param analysis 解析文本
     * @param isMatched 是否从题库匹配到（true = 正确答案，标红）
     */
    fun show(question: String, answer: String, analysis: String, isMatched: Boolean) {
        if (isShowing) {
            dismiss()
        }

        val root = FrameLayout(context).apply {
            setBackgroundColor(0x00000000) // 完全透明根布局
        }

        // 内容卡片
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            background = createCardBackground()
            elevation = dpToPx(8).toFloat()
        }

        // 标题栏（拖拽区域 + 关闭按钮）
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
            setTextColor(0xFF333333.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val closeBtn = TextView(context).apply {
            text = "✕"
            textSize = 20f
            setTextColor(0xFF999999.toInt())
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dpToPx(36), dpToPx(36))
            setOnClickListener { dismiss() }
        }

        titleBar.addView(dragHint)
        titleBar.addView(closeBtn)
        card.addView(titleBar)

        // 分隔线
        card.addView(createDivider())

        // 滚动内容区
        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(320) // 最大高度
            )
            setBackgroundColor(0x00FFFFFF) // 透明
        }

        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
        }

        // 题目
        contentLayout.addView(createLabel("题目"))
        contentLayout.addView(createContentText(question, 15f, 0xFF333333.toInt()))
        contentLayout.addView(createSpacer())

        // 答案（匹配到则标红）
        contentLayout.addView(createLabel(if (isMatched) "答案 ✅ 已匹配题库" else "答案 ⚠️ 未匹配题库"))
        val answerColor = if (isMatched) 0xFFE53935.toInt() else 0xFF2E7D32.toInt() // 匹配=红色，未匹配=绿色
        contentLayout.addView(createContentText(answer, 17f, answerColor, true))
        contentLayout.addView(createSpacer())

        // 解析
        if (analysis.isNotBlank()) {
            contentLayout.addView(createLabel("解析"))
            contentLayout.addView(createContentText(analysis, 14f, 0xFF666666.toInt()))
        }

        // 未匹配提示
        if (!isMatched) {
            contentLayout.addView(createSpacer())
            contentLayout.addView(createContentText(
                "提示：未在题库中找到完全匹配的题目，以上为 OCR 识别结果。",
                12f, 0xFF999999.toInt()
            ))
        }

        scrollView.addView(contentLayout)
        card.addView(scrollView)
        root.addView(card)

        // 布局参数
        val params = WindowManager.LayoutParams(
            dpToPx(340), // 宽度
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dpToPx(20)
            y = dpToPx(120)
        }

        // 拖拽监听
        card.setOnTouchListener(DragTouchListener(params, root))

        container = root
        try {
            windowManager.addView(root, params)
            isShowing = true
            Log.d(TAG, "Result window shown, matched=$isMatched")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show result window", e)
        }

        // 15 秒后自动关闭
        Handler(Looper.getMainLooper()).postDelayed({
            if (isShowing) dismiss()
        }, 15000)
    }

    fun dismiss() {
        container?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Dismiss failed", e)
            }
            container = null
            isShowing = false
        }
    }

    fun isShowing(): Boolean = isShowing

    private fun createCardBackground(): android.graphics.drawable.Drawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(16).toFloat()
            setColor(0xF0FFFFFF.toInt()) // 白色半透明背景
        }
    }

    private fun createDivider(): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(1)
            ).apply { topMargin = dpToPx(8); bottomMargin = dpToPx(8) }
            setBackgroundColor(0xFFE0E0E0.toInt())
        }
    }

    private fun createLabel(text: String): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 12f
            setTextColor(0xFF888888.toInt())
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
            if (isBold) {
                paint.isFakeBoldText = true
            }
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
}
