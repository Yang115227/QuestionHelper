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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.questionhelper.QuestionApp
import com.questionhelper.data.Question
import com.questionhelper.data.QuestionRepository
import com.questionhelper.ui.theme.QuestionHelperTheme
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    val repo = remember { QuestionRepository(QuestionApp.database.questionDao()) }
    var questions by remember { mutableStateOf<List<Question>>(emptyList()) }
    var subjects by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedSubject by remember { mutableStateOf("全部") }
    var showDeleteDialog by remember { mutableStateOf<Question?>(null) }
    var showPracticeDialog by remember { mutableStateOf(false) }

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
                    if (mode != "wrong") {
                        IconButton(onClick = { showPracticeDialog = true }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "练习")
                        }
                    }
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
                items(questions, key = { it.id }) { q ->
                    QuestionItemCard(
                        question = q,
                        onClick = {
                            val intent = Intent(context, PracticeActivity::class.java).apply {
                                putExtra("mode", "single")
                                putExtra("questionId", q.id)
                            }
                            context.startActivity(intent)
                        },
                        onLongClick = {
                            showDeleteDialog = q
                        }
                    )
                }
            }
        }
    }

    // 删除确认对话框
    showDeleteDialog?.let { q ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("确认删除") },
            text = { Text("确定要删除这道题吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repo.deleteQuestion(q)
                        }
                        showDeleteDialog = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("取消")
                }
            }
        )
    }

    // 练习模式选择对话框
    if (showPracticeDialog) {
        AlertDialog(
            onDismissRequest = { showPracticeDialog = false },
            title = { Text("选择练习模式") },
            text = {
                Column {
                    Card(
                        onClick = {
                            showPracticeDialog = false
                            val intent = Intent(context, PracticeActivity::class.java).apply {
                                putExtra("mode", "order")
                                putExtra("subject", selectedSubject)
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.FormatListNumbered, contentDescription = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("顺序练习", style = MaterialTheme.typography.titleSmall)
                                Text("按题目顺序逐一练习", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        onClick = {
                            showPracticeDialog = false
                            val intent = Intent(context, PracticeActivity::class.java).apply {
                                putExtra("mode", "random")
                                putExtra("subject", selectedSubject)
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Shuffle, contentDescription = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("随机练习", style = MaterialTheme.typography.titleSmall)
                                Text("随机打乱题目顺序练习", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        onClick = {
                            showPracticeDialog = false
                            val intent = Intent(context, PracticeActivity::class.java).apply {
                                putExtra("mode", "wrong")
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Error, contentDescription = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("错题重做", style = MaterialTheme.typography.titleSmall)
                                Text("仅练习错题本中的题目", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPracticeDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun QuestionItemCard(
    question: Question,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
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
            IconButton(onClick = onLongClick) {
                Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}