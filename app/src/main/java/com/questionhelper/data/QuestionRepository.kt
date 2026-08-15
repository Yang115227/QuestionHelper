package com.questionhelper.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class QuestionRepository(private val dao: QuestionDao) {

    // ... 原有代码保持不变 ...

    /**
     * 智能搜索题目：先精确匹配清洗后的文本，再尝试包含匹配，最后用编辑距离模糊匹配
     */
    suspend fun searchQuestionSmart(ocrText: String): Question? = withContext(Dispatchers.IO) {
        val cleaned = cleanForMatch(ocrText)
        if (cleaned.isBlank()) return@withContext null

        // 1. 精确匹配（清洗后完全相等）
        dao.getAllQuestionsList().find { question ->
            cleanForMatch(question.content) == cleaned
        }?.let { return@withContext it }

        // 2. 包含匹配（题库题目包含 OCR 文本，或 OCR 包含题库题目，且长度足够）
        val candidates = dao.getAllQuestionsList()
        val minLength = 4 // 避免太短的匹配
        candidates.find { question ->
            val qClean = cleanForMatch(question.content)
            if (qClean.length < minLength || cleaned.length < minLength) false
            else qClean.contains(cleaned) || cleaned.contains(qClean)
        }?.let { return@withContext it }

        // 3. 编辑距离相似度匹配（阈值 0.75）
        var bestQuestion: Question? = null
        var bestScore = 0.0
        for (question in candidates) {
            val qClean = cleanForMatch(question.content)
            val score = similarity(cleaned, qClean)
            if (score > bestScore) {
                bestScore = score
                bestQuestion = question
            }
        }
        if (bestScore >= 0.75) bestQuestion else null
    }

    /**
     * 清洗文本：去除所有非字母数字和中文的字符，统一小写
     */
    private fun cleanForMatch(input: String): String {
        return input
            .lowercase()
            .filter { it.isLetterOrDigit() || it.code in 0x4E00..0x9FFF } // 保留中文、字母、数字
    }

    /**
     * 计算两个字符串的相似度（基于编辑距离）
     * 返回值在 0.0 ~ 1.0 之间
     */
    private fun similarity(a: String, b: String): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val maxLen = maxOf(a.length, b.length)
        if (maxLen == 0) return 1.0
        val distance = levenshteinDistance(a, b)
        return 1.0 - distance.toDouble() / maxLen
    }

    /**
     * 编辑距离算法
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // 删除
                    dp[i][j - 1] + 1,      // 插入
                    dp[i - 1][j - 1] + cost // 替换
                )
            }
        }
        return dp[m][n]
    }
}