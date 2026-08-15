package com.questionhelper.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class QuestionRepository(private val dao: QuestionDao) {
    val allQuestions: Flow<List<Question>> = dao.getAllQuestions()
    val wrongQuestions: Flow<List<Question>> = dao.getWrongQuestions()
    val allSubjects: Flow<List<String>> = dao.getAllSubjects()

    suspend fun searchQuestion(content: String): Question? = withContext(Dispatchers.IO) {
        dao.searchByContent("%$content%")
    }

    suspend fun insertQuestion(question: Question) = withContext(Dispatchers.IO) {
        dao.insertQuestion(question)
    }

    suspend fun insertQuestions(questions: List<Question>) = withContext(Dispatchers.IO) {
        dao.insertQuestions(questions)
    }

    suspend fun markWrong(id: Long) = withContext(Dispatchers.IO) {
        dao.markWrong(id)
    }

    suspend fun clearWrong(id: Long) = withContext(Dispatchers.IO) {
        dao.clearWrong(id)
    }

    suspend fun deleteQuestion(question: Question) = withContext(Dispatchers.IO) {
        dao.deleteQuestion(question)
    }

    suspend fun deleteQuestionById(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteQuestionById(id)
    }

    suspend fun getQuestionCount(): Int = withContext(Dispatchers.IO) {
        dao.getQuestionCount()
    }

    suspend fun getWrongCount(): Int = withContext(Dispatchers.IO) {
        dao.getWrongCount()
    }

    suspend fun getQuestionById(id: Long): Question? = withContext(Dispatchers.IO) {
        dao.getQuestionById(id)
    }

    fun getQuestionsBySubject(subject: String): Flow<List<Question>> {
        return dao.getQuestionsBySubject(subject)
    }

    suspend fun getOrderedQuestions(subject: String = "全部"): List<Question> = withContext(Dispatchers.IO) {
        if (subject == "全部") dao.getAllQuestionsList() else dao.getQuestionsOrderById(subject)
    }

    suspend fun getRandomQuestions(subject: String = "全部"): List<Question> = withContext(Dispatchers.IO) {
        if (subject == "全部") dao.getAllQuestionsRandom() else dao.getQuestionsRandom(subject)
    }

    suspend fun deleteQuestionsBySubject(subject: String) = withContext(Dispatchers.IO) {
        dao.deleteQuestionsBySubject(subject)
    }

    suspend fun getQuestionCountBySubject(subject: String): Int = withContext(Dispatchers.IO) {
        dao.getQuestionCountBySubject(subject)
    }

    suspend fun getLatestQuestionBySubject(subject: String): Question? = withContext(Dispatchers.IO) {
        dao.getLatestQuestionBySubject(subject)
    }

    /**
     * 智能搜索题目：优化清洗与多策略相似度
     */
    suspend fun searchQuestionSmart(ocrText: String): Question? = withContext(Dispatchers.IO) {
        val cleanedOcr = cleanForMatch(ocrText)
        if (cleanedOcr.isBlank()) return@withContext null

        val allQuestions = dao.getAllQuestionsList()
        if (allQuestions.isEmpty()) return@withContext null

        // 1. 精确匹配（清洗后完全相等）
        allQuestions.find { question ->
            val stem = extractQuestionStem(question.content)
            cleanForMatch(stem) == cleanedOcr
        }?.let { return@withContext it }

        // 2. 包含匹配（双向）
        val minLength = 4
        allQuestions.find { question ->
            val stem = cleanForMatch(extractQuestionStem(question.content))
            if (stem.length < minLength || cleanedOcr.length < minLength) false
            else stem.contains(cleanedOcr) || cleanedOcr.contains(stem)
        }?.let { return@withContext it }

        // 3. 相似度评分，选择最佳
        var bestQuestion: Question? = null
        var bestScore = 0.0
        for (question in allQuestions) {
            val stem = cleanForMatch(extractQuestionStem(question.content))
            val score = maxOf(
                levenshteinSimilarity(cleanedOcr, stem),
                jaccardSimilarity(cleanedOcr, stem)
            )
            if (score > bestScore) {
                bestScore = score
                bestQuestion = question
            }
        }

        // 阈值可调：0.6 较宽松，0.75 较严格
        if (bestScore >= 0.6) bestQuestion else null
    }

    /**
     * 从 content 中提取题干（第一行非选项行）
     */
    private fun extractQuestionStem(content: String): String {
        val lines = content.lines()
        // 如果第一行不是选项，直接返回第一行；否则尝试找第一个非选项行
        val optionPattern = Regex("^\\s*[A-Da-d]\\s*[.、．:：）)]")
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !optionPattern.containsMatchIn(trimmed)) {
                return trimmed
            }
        }
        // 如果全是选项，返回整个内容
        return content
    }

    /**
     * 清洗文本：去除所有非字母数字和中文的字符，统一小写，去除常见虚词
     */
    private fun cleanForMatch(input: String): String {
        val stopWords = setOf("的", "是", "在", "了", "和", "与", "或", "及", "等", "为", "被", "把", "吗", "呢", "啊")
        return input
            .lowercase()
            .filter { it.isLetterOrDigit() || it.code in 0x4E00..0x9FFF }
            .let { filtered ->
                // 简单去除停用词（可选，根据情况启用）
                // filtered.filter { it.toString() !in stopWords }
                filtered
            }
    }

    /**
     * 基于编辑距离的相似度，返回值 0~1
     */
    private fun levenshteinSimilarity(a: String, b: String): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val maxLen = maxOf(a.length, b.length)
        if (maxLen == 0) return 1.0
        val distance = levenshteinDistance(a, b)
        return 1.0 - distance.toDouble() / maxLen
    }

    /**
     * 基于字符集合的 Jaccard 相似度
     */
    private fun jaccardSimilarity(a: String, b: String): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val setA = a.toSet()
        val setB = b.toSet()
        val intersection = setA.intersect(setB).size
        val union = setA.union(setB).size
        return if (union == 0) 0.0 else intersection.toDouble() / union
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