package com.questionhelper.parser

import android.util.Log
import com.questionhelper.data.Question
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream

object ExcelParser {
    private const val TAG = "ExcelParser"

    fun parse(inputStream: InputStream): List<Question> {
        val questions = mutableListOf<Question>()
        var workbook: org.apache.poi.ss.usermodel.Workbook? = null

        try {
            workbook = WorkbookFactory.create(inputStream)

            // 遍历所有 Sheet
            for (sheetIndex in 0 until workbook.numberOfSheets) {
                val sheet = workbook.getSheetAt(sheetIndex)
                val sheetName = sheet.sheetName ?: "默认"

                if (sheet.lastRowNum < 1) continue // 只有标题行或空表

                // 第一行是标题，从第二行（索引1）开始读
                for (rowIndex in 1..sheet.lastRowNum) {
                    val row = sheet.getRow(rowIndex) ?: continue

                    try {
                        // 第一列：题目
                        val content = row.getCell(0)?.toString()?.trim()
                        if (content.isNullOrEmpty()) continue

                        // 第二列：正确答案
                        val answer = row.getCell(1)?.toString()?.trim() ?: ""

                        // 第三列及以后：选项
                        val optionsList = mutableListOf<String>()
                        for (colIndex in 2 until row.lastCellNum.coerceAtLeast(0)) {
                            val optionCell = row.getCell(colIndex)
                            val optionText = optionCell?.toString()?.trim()
                            if (!optionText.isNullOrEmpty()) {
                                // 列索引 2 对应选项A，3对应B，以此类推
                                val optionLabel = ('A' + (colIndex - 2)).toString()
                                optionsList.add("$optionLabel. $optionText")
                            }
                        }
                        val options = if (optionsList.isNotEmpty()) optionsList.joinToString("|") else ""

                        questions.add(
                            Question(
                                content = content,
                                options = options,
                                answer = answer,
                                analysis = "",
                                subject = sheetName
                            )
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Parse row $rowIndex in sheet '$sheetName' failed", e)
                        continue
                    }
                }
            }

            Log.d(TAG, "Parsed ${questions.size} questions from ${workbook.numberOfSheets} sheets")
        } catch (e: NoClassDefFoundError) {
            Log.e(TAG, "POI class missing on Android", e)
        } catch (e: ExceptionInInitializerError) {
            Log.e(TAG, "POI initialization failed on Android", e)
        } catch (e: Exception) {
            Log.e(TAG, "Parse Excel failed", e)
        } finally {
            try {
                workbook?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Close workbook failed", e)
            }
            try {
                inputStream.close()
            } catch (e: Exception) {
                Log.e(TAG, "Close inputStream failed", e)
            }
        }

        return questions
    }
}
