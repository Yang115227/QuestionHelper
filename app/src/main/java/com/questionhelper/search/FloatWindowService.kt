    /**
     * 显示搜索结果悬浮窗
     */
    fun showResult(question: String, answer: String, analysis: String, isMatched: Boolean) {
        try {
            if (resultView == null) {
                resultView = FloatResultView(this)
            }
            resultView?.show(question, answer, analysis, isMatched)
        } catch (e: Exception) {
            Log.e(TAG, "showResult failed", e)
            Toast.makeText(this, "显示结果失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
