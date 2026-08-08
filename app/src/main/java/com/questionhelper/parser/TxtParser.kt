package com.questionhelper.parser

import com.questionhelper.data.Question
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

object TxtParser {
    fun parse(inputStream: InputStream): List<Question> {
        val questions = mutableListOf<Question>()
        val reader = BufferedReader(InputStreamReader(inputStream))
        val lines = reader.readLines()

        var content = ""
        var options = ""
        var answer = ""
        var analysis = ""
        var subject = "默认"

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() || trimmed == "---" -> {
                    if (content.isNotEmpty()) {
                        questions.add(Question(
                            content = content,
                            options = options,
                            answer = answer,
                            analysis = analysis,
                            subject = subject
                        ))
                        content = ""; options = ""; answer = ""; analysis = ""
                    }
                }
                trimmed.startsWith("答案：") || trimmed.startsWith("答案:") -> {
                    answer = trimmed.substringAfter("：").substringAfter(":").trim()
                }
                trimmed.startsWith("解析：") || trimmed.startsWith("解析:") -> {
                    analysis = trimmed.substringAfter("：").substringAfter(":").trim()
                }
                trimmed.startsWith("科目：") || trimmed.startsWith("科目:") -> {
                    subject = trimmed.substringAfter("：").substringAfter(":").trim()
                }
                trimmed.contains("|") || trimmed.contains(Regex("^[A-D][.．]")) -> {
                    options = trimmed.replace("．", ".").replace("|", "|")
                }
                else -> {
                    if (content.isEmpty()) content = trimmed else content += "\n$trimmed"
                }
            }
        }

        if (content.isNotEmpty()) {
            questions.add(Question(
                content = content,
                options = options,
                answer = answer,
                analysis = analysis,
                subject = subject
            ))
        }

        return questions
    }
}
