package com.questionhelper.bank

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.questionhelper.QuestionApp
import com.questionhelper.data.Question
import com.questionhelper.data.QuestionRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WrongBookScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { QuestionRepository(QuestionApp.database.questionDao()) }
    var questions by remember { mutableStateOf<List<Question>>(emptyList()) }
    var showDeleteDialog by remember { mutableStateOf<Question?>(null) }

    LaunchedEffect(Unit) {
        repo.wrongQuestions.collect { questions = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("错题本") },
                navigationIcon = {
                    IconButton(onClick = { (context as? ComponentActivity)?.finish() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(questions, key = { it.id }) { q ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        val intent = Intent(context, PracticeActivity::class.java).apply {
                            putExtra("mode", "single")
                            putExtra("questionId", q.id)
                        }
                        context.startActivity(intent)
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(q.content.take(80) + if (q.content.length > 80) "..." else "")
                            Spacer(modifier = Modifier.height(4.dp))
                            AssistChip(onClick = {}, label = { Text(q.subject) })
                        }
                        IconButton(onClick = { showDeleteDialog = q }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }

    showDeleteDialog?.let { q ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("删除题目") },
            text = { Text("确定彻底删除这道题吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repo.deleteQuestionById(q.id)
                        }
                        showDeleteDialog = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("取消") }
            }
        )
    }
}