package com.questionhelper.parser

import android.util.Log
import com.questionhelper.data.Question
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream

object ExcelParser {
    private const val TAG = "ExcelParser"

    fun parse(inputStream: InputStream): List<Question> {
        val questions = mutableListOf<Question>()
        var workbook: org.apache.poi.ss.usermodel.Workbook? = null

        try {
            workbook = WorkbookFactory.create(inputStream)

            for (sheetIndex in 0 until workbook.numberOfSheets) {
                val sheet = workbook.getSheetAt(sheetIndex)
                val sheetName = sheet.sheetName?.trim()?.takeIf { it.isNotEmpty() } ?: "默认"

                if (sheet.lastRowNum < 0) {
                    Log.d(TAG, "Sheet '$sheetName' is empty")
                    continue
                }

                val firstRow = sheet.getRow(0)
                val firstCellText = firstRow?.getCell(0)?.toString()?.trim() ?: ""
                val skipHeader = firstCellText.isHeader()
                val startRow = if (skipHeader) 1 else 0

                Log.d(TAG, "Sheet '$sheetName': headerDetected=$skipHeader, totalRows=${sheet.lastRowNum + 1}")

                for (rowIndex in startRow..sheet.lastRowNum) {
                    val row = sheet.getRow(rowIndex) ?: continue

                    try {
                        // 第一列：题目（题干）
                        val stem = row.getCell(0)?.readString()
                        if (stem.isNullOrEmpty()) {
                            Log.d(TAG, "Skip row $rowIndex: empty content")
                            continue
                        }

                        // 第二列：正确答案
                        val rawAnswer = row.getCell(1)?.readString() ?: ""
                        val answer = normalizeAnswer(rawAnswer)
                        if (answer.isEmpty()) {
                            Log.w(TAG, "Row $rowIndex has empty answer, content=$stem")
                        }

                        // 第三列及以后：选项（需跳过难度列）
                        val optionsList = mutableListOf<String>()
                        val lastCell = row.lastCellNum.coerceAtLeast(0).toInt()
                        var optionIndex = 0
                        for (colIndex in 2 until lastCell) {
                            val rawValue = row.getCell(colIndex)?.readString()
                            if (rawValue.isNullOrEmpty()) continue

                            // 判断是否为难度等级，如果是则跳过，不作为选项
                            if (rawValue in setOf("初级", "中级", "高级", "技师")) {
                                Log.d(TAG, "Skip difficulty column: $rawValue at col $colIndex")
                                continue
                            }

                            val optionLabel = ('A' + optionIndex).toString()
                            optionIndex++
                            val optionText = when {
                                rawValue.matches(Regex("^[A-Z][.．、,，:：)\\s].*")) -> rawValue
                                else -> "$optionLabel. $rawValue"
                            }
                            optionsList.add(optionText)
                        }

                        // 将选项拼接到题干后面，用换行分隔，便于后续提取
                        val contentWithOptions = if (optionsList.isNotEmpty()) {
                            buildString {
                                append(stem)
                                for (option in optionsList) {
                                    append('\n')
                                    append(option)
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
                                subject = sheetName
                            )
                        )
                    } catch (e: Throwable) {
                        Log.e(TAG, "Parse row $rowIndex in sheet '$sheetName' failed", e)
                        continue
                    }
                }
            }

            Log.d(TAG, "Parsed ${questions.size} questions from ${workbook.numberOfSheets} sheets")
        } catch (e: NoClassDefFoundError) {
            Log.e(TAG, "POI class missing on Android", e)
            throw RuntimeException("Excel 解析库缺失: ${e.message}", e)
        } catch (e: ExceptionInInitializerError) {
            Log.e(TAG, "POI initialization failed on Android", e)
            throw RuntimeException("Excel 解析库初始化失败: ${e.message}", e)
        } catch (e: Throwable) {
            Log.e(TAG, "Parse Excel failed", e)
            throw RuntimeException("解析 Excel 失败: ${e.message}", e)
        } finally {
            try {
                workbook?.close()
            } catch (e: Throwable) {
                Log.e(TAG, "Close workbook failed", e)
            }
            try {
                inputStream.close()
            } catch (e: Throwable) {
                Log.e(TAG, "Close inputStream failed", e)
            }
        }

        return questions
    }

    private fun Cell.readString(): String {
        return when (cellType) {
            CellType.NUMERIC -> {
                val value = numericCellValue
                if (value == value.toInt().toDouble()) {
                    value.toInt().toString()
                } else {
                    value.toString()
                }
            }
            CellType.STRING -> stringCellValue
            CellType.BOOLEAN -> booleanCellValue.toString()
            CellType.FORMULA -> {
                try {
                    numericCellValue.toString()
                } catch (_: Throwable) {
                    stringCellValue
                }
            }
            else -> ""
        }.trim().clean()
    }

    private fun String.clean(): String {
        return this
            .replace("\u00A0", "")
            .replace("\u3000", "")
            .replace("\r", "")
            .replace("\n", "")
            .trim()
    }

    private fun String.isHeader(): Boolean {
        val lower = this.lowercase()
        return lower.contains("题目") || lower.contains("题干") ||
               lower.contains("答案") || lower.contains("选项") ||
               lower.contains("难度") ||
               lower.contains("question") || lower.contains("answer")
    }

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