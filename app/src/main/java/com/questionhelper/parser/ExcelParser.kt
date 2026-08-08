package com.questionhelper.parser

import com.questionhelper.data.Question
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream

object ExcelParser {
    fun parse(inputStream: InputStream): List<Question> {
        val questions = mutableListOf<Question>()
        val workbook = WorkbookFactory.create(inputStream)
        val sheet = workbook.getSheetAt(0)

        for (i in 1..sheet.lastRowNum) {
            val row = sheet.getRow(i) ?: continue
            try {
                val content = row.getCell(0)?.toString()?.trim() ?: continue
                val options = row.getCell(1)?.toString()?.trim() ?: ""
                val answer = row.getCell(2)?.toString()?.trim() ?: ""
                val analysis = row.getCell(3)?.toString()?.trim() ?: ""
                val subject = row.getCell(4)?.toString()?.trim() ?: "默认"

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
