package com.questionhelper.bank

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.questionhelper.QuestionApp
import com.questionhelper.data.Question
import com.questionhelper.data.QuestionRepository
import com.questionhelper.ui.theme.QuestionHelperTheme

class QuestionBankActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra("mode") ?: "all"
        setContent {
            QuestionHelperTheme {
                QuestionBankScreen(mode = mode)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionBankScreen(mode: String) {
    val context = LocalContext.current
    val repo = remember { QuestionRepository(QuestionApp.database.questionDao()) }
    var questions by remember { mutableStateOf<List<Question>>(emptyList()) }
    var subjects by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedSubject by remember { mutableStateOf("全部") }

    LaunchedEffect(Unit) {
        repo.allSubjects.collect { subjects = it }
    }

    LaunchedEffect(selectedSubject, mode) {
        if (mode == "wrong") {
            repo.wrongQuestions.collect { questions = it }
        } else if (selectedSubject == "全部") {
            repo.allQuestions.collect { questions = it }
        } else {
            repo.getQuestionsBySubject(selectedSubject).collect { questions = it }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (mode == "wrong") "错题本" else "我的题库") },
                actions = {
                    IconButton(onClick = {
                        context.startActivity(Intent(context, ImportActivity::class.java))
                    }) {
                        Icon(Icons.Default.FileUpload, contentDescription = "导入")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (mode != "wrong") {
                ScrollableTabRow(
                    selectedTabIndex = if (selectedSubject == "全部") 0 else subjects.indexOf(selectedSubject) + 1,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedSubject == "全部",
                        onClick = { selectedSubject = "全部" },
                        text = { Text("全部") }
                    )
                    subjects.forEach { subject ->
                        Tab(
                            selected = selectedSubject == subject,
                            onClick = { selectedSubject = subject },
                            text = { Text(subject) }
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(questions) { q ->
                    QuestionItemCard(q, onClick = {
                        val intent = Intent(context, PracticeActivity::class.java).apply {
                            putExtra("questionId", q.id)
                        }
                        context.startActivity(intent)
                    })
                }
            }
        }
    }
}

@Composable
fun QuestionItemCard(question: Question, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = question.content.take(80) + if (question.content.length > 80) "..." else "",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row {
                AssistChip(
                    onClick = { },
                    label = { Text(question.subject) }
                )
                if (question.wrongCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    AssistChip(
                        onClick = { },
                        label = { Text("错题 ${question.wrongCount}") },
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = MaterialTheme.colorScheme.error
                        )
                    )
                }
            }
        }
    }
}
