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

    // 新增：顺序练习
    suspend fun getOrderedQuestions(subject: String = "全部"): List<Question> = withContext(Dispatchers.IO) {
        if (subject == "全部") dao.getAllQuestionsList() else dao.getQuestionsOrderById(subject)
    }

    // 新增：随机练习
    suspend fun getRandomQuestions(subject: String = "全部"): List<Question> = withContext(Dispatchers.IO) {
        if (subject == "全部") dao.getAllQuestionsRandom() else dao.getQuestionsRandom(subject)
    }

    // ===== 新增：按分类管理 =====
    suspend fun deleteQuestionsBySubject(subject: String) = withContext(Dispatchers.IO) {
        dao.deleteQuestionsBySubject(subject)
    }

    suspend fun getQuestionCountBySubject(subject: String): Int = withContext(Dispatchers.IO) {
        dao.getQuestionCountBySubject(subject)
    }

    suspend fun getLatestQuestionBySubject(subject: String): Question? = withContext(Dispatchers.IO) {
        dao.getLatestQuestionBySubject(subject)
    }
}