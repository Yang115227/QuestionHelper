package com.questionhelper.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionDao {
    @Query("SELECT * FROM questions ORDER BY createTime DESC")
    fun getAllQuestions(): Flow<List<Question>>

    @Query("SELECT * FROM questions WHERE subject = :subject ORDER BY createTime DESC")
    fun getQuestionsBySubject(subject: String): Flow<List<Question>>

    @Query("SELECT * FROM questions WHERE wrongCount > 0 ORDER BY wrongCount DESC, lastWrongTime DESC")
    fun getWrongQuestions(): Flow<List<Question>>

    @Query("SELECT * FROM questions WHERE content LIKE :keyword LIMIT 1")
    suspend fun searchByContent(keyword: String): Question?

    @Query("SELECT * FROM questions WHERE id = :id")
    suspend fun getQuestionById(id: Long): Question?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestion(question: Question): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<Question>)

    @Update
    suspend fun updateQuestion(question: Question)

    @Delete
    suspend fun deleteQuestion(question: Question)

    @Query("DELETE FROM questions WHERE id = :id")
    suspend fun deleteQuestionById(id: Long)

    @Query("SELECT DISTINCT subject FROM questions")
    fun getAllSubjects(): Flow<List<String>>

    @Query("UPDATE questions SET wrongCount = wrongCount + 1, lastWrongTime = :time WHERE id = :id")
    suspend fun markWrong(id: Long, time: Long = System.currentTimeMillis())

    @Query("UPDATE questions SET wrongCount = 0 WHERE id = :id")
    suspend fun clearWrong(id: Long)

    @Query("SELECT COUNT(*) FROM questions")
    suspend fun getQuestionCount(): Int

    @Query("SELECT COUNT(*) FROM questions WHERE wrongCount > 0")
    suspend fun getWrongCount(): Int

    // 新增：顺序练习（按ID排序）
    @Query("SELECT * FROM questions WHERE subject = :subject ORDER BY id ASC")
    suspend fun getQuestionsOrderById(subject: String): List<Question>

    // 新增：随机练习
    @Query("SELECT * FROM questions WHERE subject = :subject ORDER BY RANDOM()")
    suspend fun getQuestionsRandom(subject: String): List<Question>

    // 新增：获取所有题目（非Flow，用于练习模式）
    @Query("SELECT * FROM questions ORDER BY id ASC")
    suspend fun getAllQuestionsList(): List<Question>

    @Query("SELECT * FROM questions ORDER BY RANDOM()")
    suspend fun getAllQuestionsRandom(): List<Question>

    // ===== 新增：按分类管理 =====
    @Query("DELETE FROM questions WHERE subject = :subject")
    suspend fun deleteQuestionsBySubject(subject: String)

    @Query("SELECT COUNT(*) FROM questions WHERE subject = :subject")
    suspend fun getQuestionCountBySubject(subject: String): Int

    @Query("SELECT * FROM questions WHERE subject = :subject ORDER BY createTime DESC LIMIT 1")
    suspend fun getLatestQuestionBySubject(subject: String): Question?
}

@Dao
interface UserRecordDao {
    @Insert
    suspend fun insertRecord(record: UserRecord)

    @Query("SELECT * FROM user_records WHERE questionId = :questionId ORDER BY timestamp DESC")
    fun getRecordsByQuestion(questionId: Long): Flow<List<UserRecord>>
}