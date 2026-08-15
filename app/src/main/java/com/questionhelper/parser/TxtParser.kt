package com.questionhelper.parser

import android.util.Log
import com.questionhelper.data.Question
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

object TxtParser {
    private const val TAG = "TxtParser"

    /**
     * 解析 TXT 题库文件。
     * 支持两种常见格式：
     * 1. 每行一个题目，字段用 Tab 或竖线 | 分隔：
     *    题目<TAB>答案<TAB>选项A<TAB>选项B<TAB>选项C<TAB>选项D
     *    题目|答案|选项A|选项B|选项C|选项D
     * 2. 每行一个题目，字段用逗号分隔（简单处理）：
     *    题目,答案,选项A,选项B,选项C,选项D
     *
     * 第一行如果是标题（包含“题目”、“答案”等），自动跳过。
     *
     * @param inputStream TXT 文件输入流
     * @param defaultSubject 默认科目名称（如果文件未提供）
     */
    fun parse(inputStream: InputStream, defaultSubject: String = "默认"): List<Question> {
        val questions = mutableListOf<Question>()
        var lineNumber = 0

        try {
            BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                var isFirstLine = true
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    lineNumber++
                    val currentLine = line ?: continue
                    if (currentLine.isBlank()) continue

                    // 尝试识别分隔符：优先 Tab，其次竖线，最后逗号
                    val delimiter = when {
                        currentLine.contains('\t') -> '\t'
                        currentLine.contains('|') -> '|'
                        else -> ','
                    }
                    val parts = currentLine.split(delimiter).map { it.trim() }
                    if (parts.size < 2) {
                        Log.w(TAG, "Line $lineNumber has insufficient fields (${parts.size}), skip")
                        continue
                    }

                    // 检查并跳过标题行
                    if (isFirstLine && parts[0].isHeader()) {
                        Log.d(TAG, "Header detected on line $lineNumber, skip")
                        isFirstLine = false
                        continue
                    }
                    isFirstLine = false

                    val stem = parts[0].ifEmpty { continue }
                    val rawAnswer = parts.getOrNull(1)?.trim() ?: ""
                    val answer = normalizeAnswer(rawAnswer)

                    // 选项从第三列开始
                    val optionsList = mutableListOf<String>()
                    if (parts.size > 2) {
                        for (i in 2 until parts.size) {
                            val rawOption = parts[i].ifEmpty { continue }
                            val optionLabel = ('A' + (i - 2)).toString()
                            val optionText = if (rawOption.matches(Regex("^[A-Z][.．、,，:：)\\s].*"))) {
                                rawOption
                            } else {
                                "$optionLabel. $rawOption"
                            }
                            optionsList.add(optionText)
                        }
                    }

                    // 将选项拼接到题干，用换行分隔
                    val contentWithOptions = if (optionsList.isNotEmpty()) {
                        buildString {
                            append(stem)
                            for (opt in optionsList) {
                                append('\n')
                                append(opt)
                            }
                        }
                    } else {
                        stem
                    }

                    val options = optionsList.joinToString("|")

                    questions.add(
                        Question(
                            content = contentWithOptions,
                            options = options,
                            answer = answer,
                            analysis = "",
                            subject = defaultSubject
                        )
                    )
                }
            }
            Log.d(TAG, "Parsed ${questions.size} questions from TXT")
        } catch (e: Exception) {
            Log.e(TAG, "Parse TXT failed at line $lineNumber", e)
            throw RuntimeException("解析 TXT 失败: ${e.message}")
        }

        return questions
    }

    /**
     * 判断字符串是否像标题行（包含“题目”、“答案”等关键词）
     */
    private fun String.isHeader(): Boolean {
        val lower = this.lowercase()
        return lower.contains("题目") || lower.contains("题干") ||
               lower.contains("答案") || lower.contains("选项") ||
               lower.contains("question") || lower.contains("answer")
    }

    /**
     * 规范化答案，与 ExcelParser 保持一致。
     */
    private fun normalizeAnswer(answer: String): String {
        if (answer.isEmpty()) return ""

        when (answer) {
            "正确", "对", "T", "TRUE", "True", "true", "√", "是" -> return "正确"
            "错误", "错", "F", "FALSE", "False", "false", "×", "X", "否" -> return "错误"
        }

        val letters = answer
            .uppercase()
            .filter { it in 'A'..'Z' }
            .toList()
            .distinct()
            .joinToString("")

        return if (letters.isNotEmpty()) letters else answer
    }
}