package com.questionhelper.parser

import android.util.Log
import com.questionhelper.data.Question
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream

object ExcelParser {
    private const val TAG = "ExcelParser"

    // 难度关键词
    private val DIFFICULTY_KEYWORDS = setOf(
        "初级", "中级", "高级", "技师", "简单", "中等", "困难"
    )

    // 标题行关键词
    private val HEADER_KEYWORDS = listOf(
        "题目", "题干", "问题", "内容", "答案", "正确", "选项", "难度",
        "question", "answer", "option"
    )

    fun parse(inputStream: InputStream, defaultSubject: String? = null): List<Question> {
        val questions = mutableListOf<Question>()
        var workbook: org.apache.poi.ss.usermodel.Workbook? = null

        try {
            workbook = WorkbookFactory.create(inputStream)

            for (sheetIndex in 0 until workbook.numberOfSheets) {
                val sheet = workbook.getSheetAt(sheetIndex)
                val sheetName = sheet.sheetName?.trim()?.takeIf { it.isNotEmpty() } ?: "默认"

                if (sheet.lastRowNum < 0) continue

                val headerRow = sheet.getRow(0)
                val headerTexts = extractRowTexts(headerRow)
                val hasHeader = isHeaderRow(headerTexts)
                val startRow = if (hasHeader) 1 else 0

                Log.d(TAG, "Sheet '$sheetName': hasHeader=$hasHeader, startRow=$startRow")

                // 收集所有数据行
                val dataRows = mutableListOf<List<String>>()
                for (rowIndex in startRow..sheet.lastRowNum) {
                    val row = sheet.getRow(rowIndex) ?: continue
                    val texts = extractRowTexts(row)
                    if (texts.any { it.isNotBlank() }) {
                        dataRows.add(texts)
                    }
                }
                if (dataRows.isEmpty()) continue

                val columnCount = dataRows.maxOfOrNull { it.size } ?: 0
                val analysis = analyzeColumns(dataRows, columnCount)

                Log.d(
                    TAG,
                    "Columns → stem=${analysis.stemCol}, answer=${analysis.answerCol}, " +
                            "options=${analysis.optionCols}, difficulty=${analysis.difficultyCols}"
                )

                for (rowTexts in dataRows) {
                    try {
                        val stem = rowTexts.getOrNull(analysis.stemCol)?.trim().orEmpty()
                        if (stem.isEmpty()) continue

                        val rawAnswer = rowTexts.getOrNull(analysis.answerCol)?.trim().orEmpty()
                        val answer = normalizeAnswer(rawAnswer)

                        val optionsList = mutableListOf<String>()
                        var optionIndex = 0
                        for (col in analysis.optionCols) {
                            val text = rowTexts.getOrNull(col)?.trim().orEmpty()
                            if (text.isEmpty() || text in DIFFICULTY_KEYWORDS) continue

                            val optionText = if (text.matches(Regex("^[A-Za-z][.．、,，:：)\\s].*"))) {
                                text
                            } else {
                                "${('A' + optionIndex)}. $text"
                            }
                            optionIndex++
                            optionsList.add(optionText)
                        }

                        val contentWithOptions = if (optionsList.isNotEmpty()) {
                            buildString {
                                append(stem)
                                for (opt in optionsList) {
                                    append('\n')
                                    append(opt)
                                }
                            }
                        } else stem

                        questions.add(
                            Question(
                                content = contentWithOptions,
                                options = optionsList.joinToString("|"),
                                answer = answer,
                                analysis = "",
                                subject = defaultSubject ?: sheetName
                            )
                        )
                    } catch (e: Throwable) {
                        Log.e(TAG, "Parse row failed", e)
                    }
                }
            }
            Log.d(TAG, "Parsed ${questions.size} questions")
        } catch (e: Throwable) {
            Log.e(TAG, "Parse Excel failed", e)
            throw RuntimeException("解析 Excel 失败: ${e.message}", e)
        } finally {
            try { workbook?.close() } catch (_: Throwable) {}
            try { inputStream.close() } catch (_: Throwable) {}
        }
        return questions
    }

    // ---------- 辅助函数 ----------

    private fun extractRowTexts(row: Row?): List<String> {
        if (row == null) return emptyList()
        val last = row.lastCellNum.coerceAtLeast(0).toInt()
        val result = mutableListOf<String>()
        for (i in 0 until last) {
            result.add(row.getCell(i)?.readString() ?: "")
        }
        return result
    }

    private fun isHeaderRow(texts: List<String>): Boolean {
        if (texts.isEmpty()) return false
        val joined = texts.joinToString(" ").lowercase()
        return HEADER_KEYWORDS.any { joined.contains(it.lowercase()) }
    }

    private data class ColumnAnalysis(
        val stemCol: Int,
        val answerCol: Int,
        val optionCols: List<Int>,
        val difficultyCols: List<Int>
    )

    private fun analyzeColumns(dataRows: List<List<String>>, columnCount: Int): ColumnAnalysis {
        val answerScore = IntArray(columnCount)
        val difficultyScore = IntArray(columnCount)
        val optionScore = IntArray(columnCount)
        val totalLength = IntArray(columnCount)

        for (row in dataRows) {
            for (col in 0 until columnCount) {
                val text = row.getOrNull(col)?.trim().orEmpty()
                if (text.isEmpty()) continue
                if (isAnswerLike(text)) answerScore[col]++
                if (text in DIFFICULTY_KEYWORDS) difficultyScore[col]++
                if (text.matches(Regex("^[A-Za-z][.．、,，:：)\\s].*"))) optionScore[col]++
                totalLength[col] += text.length
            }
        }

        val total = dataRows.size.coerceAtLeast(1)

        // 答案列
        var answerCol = (0 until columnCount).maxByOrNull { answerScore[it] } ?: 0
        if (answerScore[answerCol] < total / 2) {
            answerCol = 1.coerceAtMost(columnCount - 1)
        }

        // 难度列
        val difficultyCols = mutableListOf<Int>()
        for (col in 0 until columnCount) {
            if (col == answerCol) continue
            if (difficultyScore[col] >= total / 2) difficultyCols.add(col)
        }

        // 题干列：剩余列中平均长度最长的
        var stemCol = -1
        var maxLen = -1
        for (col in 0 until columnCount) {
            if (col == answerCol || difficultyCols.contains(col)) continue
            if (optionScore[col] >= total / 2) continue
            if (totalLength[col] > maxLen) {
                maxLen = totalLength[col]
                stemCol = col
            }
        }
        if (stemCol == -1) stemCol = 0

        // 选项列：剩余所有列
        val optionCols = mutableListOf<Int>()
        for (col in 0 until columnCount) {
            if (col == stemCol || col == answerCol || difficultyCols.contains(col)) continue
            optionCols.add(col)
        }

        return ColumnAnalysis(stemCol, answerCol, optionCols, difficultyCols)
    }

    private fun isAnswerLike(text: String): Boolean {
        val t = text.trim()
        if (t in listOf("正确", "错误", "对", "错", "√", "×", "是", "否", "T", "F", "true", "false")) return true
        // 单个或多个字母：A、AB、ABC
        if (t.matches(Regex("^[A-Za-z]{1,6}$"))) return true
        // A,B / A、B / A B
        if (t.matches(Regex("^[A-Za-z]([,.、，\\s]+[A-Za-z])*$"))) return true
        return false
    }

    private fun Cell.readString(): String {
        return when (cellType) {
            CellType.NUMERIC -> {
                val v = numericCellValue
                if (v == v.toInt().toDouble()) v.toInt().toString() else v.toString()
            }
            CellType.STRING -> stringCellValue
            CellType.BOOLEAN -> booleanCellValue.toString()
            CellType.FORMULA -> try { numericCellValue.toString() } catch (_: Throwable) { stringCellValue }
            else -> ""
        }.trim().clean()
    }

    private fun String.clean(): String = this
        .replace("\u00A0", "")
        .replace("\u3000", "")
        .replace("\r", "")
        .replace("\n", "")
        .trim()

    private fun normalizeAnswer(answer: String): String {
        if (answer.isEmpty()) return ""
        when (answer) {
            "正确", "对", "T", "TRUE", "True", "true", "√", "是" -> return "正确"
            "错误", "错", "F", "FALSE", "False", "false", "×", "X", "否" -> return "错误"
        }
        val letters = answer.uppercase().filter { it in 'A'..'Z' }.toList().distinct().joinToString("")
        return if (letters.isNotEmpty()) letters else answer
    }
}