package com.questionhelper.parser

import com.questionhelper.data.Question
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream

object ExcelParser {
    fun parse(inputStream: InputStream, defaultCategory: String = "默认"): List<Question> {
        val questions = mutableListOf<Question>()
        val workbook = WorkbookFactory.create(inputStream)
        val sheet = workbook.getSheetAt(0)

        // 表头：内容、选项、答案、解析、科目（科目可选）
        for (i in 1..sheet.lastRowNum) {
            val row = sheet.getRow(i) ?: continue
            try {
                val content = row.getCell(0)?.toString()?.trim() ?: continue
                val options = row.getCell(1)?.toString()?.trim() ?: ""
                val answer = row.getCell(2)?.toString()?.trim() ?: ""
                val analysis = row.getCell(3)?.toString()?.trim() ?: ""
                val subject = row.getCell(4)?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: defaultCategory

                if (content.isNotEmpty()) {
                    questions.add(Question(
                        content = content,
                        options = options,
                        answer = answer,
                        analysis = analysis,
                        subject = subject
                    ))
                }
            } catch (e: Exception) {
                continue
            }
        }

        workbook.close()
        return questions
    }
}