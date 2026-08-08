package com.questionhelper.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "questions")
data class Question(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val content: String,
    val options: String = "",
    val answer: String = "",
    val analysis: String = "",
    val subject: String = "默认",
    val source: String = "",
    val wrongCount: Int = 0,
    val lastWrongTime: Long? = null,
    val createTime: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_records")
data class UserRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val questionId: Long,
    val isCorrect: Boolean,
    val userAnswer: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
