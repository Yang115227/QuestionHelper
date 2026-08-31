package com.questionhelper.bank

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.questionhelper.QuestionApp
import com.questionhelper.R
import com.questionhelper.data.Question
import com.questionhelper.data.QuestionRepository
import com.questionhelper.ui.components.BackTopAppBar
import com.questionhelper.ui.components.DeleteConfirmDialog
import kotlinx.coroutines.launch

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
            BackTopAppBar(
                title = stringResource(R.string.wrong_book_title),
                onBack = { (context as? ComponentActivity)?.finish() }
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
                            // 只显示题干（不包含选项），避免内容过长
                            val stem = extractQuestionStem(q.content)
                            Text(
                                text = stem.take(80) + if (stem.length > 80) "..." else "",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            AssistChip(onClick = {}, label = { Text(q.subject) })
                        }
                        IconButton(onClick = { showDeleteDialog = q }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }

    showDeleteDialog?.let { q ->
        DeleteConfirmDialog(
            title = stringResource(R.string.wrong_delete_confirm_title),
            message = stringResource(R.string.wrong_delete_confirm_message),
            onConfirm = {
                scope.launch { repo.deleteQuestionById(q.id) }
                showDeleteDialog = null
            },
            onDismiss = { showDeleteDialog = null }
        )
    }
}

/**
 * 从题目内容中提取题干（所有非选项行），与练习界面保持一致。
 * 支持 A-Z 字母选项。
 */
private fun extractQuestionStem(content: String): String {
    val optionPattern = Regex("^\\s*[A-Za-z]\\s*[.、．:：）)]")
    val stemLines = mutableListOf<String>()
    for (line in content.lines()) {
        val trimmed = line.trim()
        if (trimmed.isNotEmpty() && !optionPattern.containsMatchIn(trimmed)) {
            stemLines.add(trimmed)
        }
    }
    return if (stemLines.isEmpty()) content.trim() else stemLines.joinToString("\n")
}