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

    suspend fun getQuestionCount(): Int = withContext(Dispatchers.IO) {
        dao.getQuestionCount()
    }

    suspend fun getWrongCount(): Int = withContext(Dispatchers.IO) {
        dao.getWrongCount()
    }

    fun getQuestionsBySubject(subject: String): Flow<List<Question>> {
        return dao.getQuestionsBySubject(subject)
    }
}
