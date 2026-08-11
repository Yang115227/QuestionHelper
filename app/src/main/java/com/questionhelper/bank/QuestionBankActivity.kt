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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.questionhelper.QuestionApp
import com.questionhelper.R
import com.questionhelper.data.Question
import com.questionhelper.data.QuestionRepository
import com.questionhelper.ui.components.BackTopAppBar
import com.questionhelper.ui.components.DeleteConfirmDialog
import com.questionhelper.ui.components.DeleteButton
import com.questionhelper.ui.components.EmptyStateMessage
import com.questionhelper.ui.theme.QuestionHelperTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class SubjectGroup(
    val subject: String,
    val count: Int,
    val updateTime: Long,
    val typeLabel: String
)

class QuestionBankActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra("mode") ?: "manage"
        setContent {
            QuestionHelperTheme {
                if (mode == "wrong") {
                    WrongBookScreen()
                } else {
                    QuestionManageScreen()
                }
            }
        }
    }
}

@Composable
fun QuestionManageScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { QuestionRepository(QuestionApp.database.questionDao()) }

    var subjectGroups by remember { mutableStateOf<List<SubjectGroup>>(emptyList()) }
    var showDeleteDialog by remember { mutableStateOf<SubjectGroup?>(null) }

    // 加载分组数据
    LaunchedEffect(Unit) {
        repo.allQuestions.collect { list ->
            val grouped = list.groupBy { it.subject }
            subjectGroups = grouped.map { (subject, questions) ->
                val count = questions.size
                val latestTime = questions.maxOfOrNull { it.createTime } ?: System.currentTimeMillis()
                val typeLabel = detectType(questions)
                SubjectGroup(subject, count, latestTime, typeLabel)
            }.sortedByDescending { it.updateTime }
        }
    }

    Scaffold(
        topBar = {
            BackTopAppBar(
                title = stringResource(R.string.bank_title),
                onBack = { (context as? ComponentActivity)?.finish() },
                actions = {
                    // 导入入口
                    IconButton(onClick = {
                        context.startActivity(Intent(context, ImportActivity::class.java))
                    }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.bank_import))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(subjectGroups, key = { it.subject }) { group ->
                SubjectManageItem(
                    group = group,
                    onClick = {
                        // 进入练习选择页
                        val intent = Intent(context, PracticeSelectActivity::class.java).apply {
                            putExtra("subject", group.subject)
                            putExtra("count", group.count)
                        }
                        context.startActivity(intent)
                    },
                    onDelete = {
                        showDeleteDialog = group
                    }
                )
                Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            }
        }

        if (subjectGroups.isEmpty()) {
            EmptyStateMessage(
                primaryText = stringResource(R.string.bank_empty_primary),
                secondaryText = stringResource(R.string.bank_empty_secondary),
                modifier = Modifier.padding(padding)
            )
        }
    }

    // 删除确认
    showDeleteDialog?.let { group ->
        DeleteConfirmDialog(
            title = stringResource(R.string.bank_delete_confirm_title),
            message = stringResource(R.string.bank_delete_confirm_message, group.subject, group.count),
            onConfirm = {
                scope.launch {
                    repo.deleteQuestionsBySubject(group.subject)
                }
                showDeleteDialog = null
            },
            onDismiss = { showDeleteDialog = null }
        )
    }
}

@Composable
fun SubjectManageItem(
    group: SubjectGroup,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.subject,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (group.typeLabel.isNotEmpty()) {
                Text(
                    text = group.typeLabel,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Row(
                modifier = Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.bank_subject_update, formatDate(group.updateTime)),
                    fontSize = 13.sp,
                    color = Color(0xFF999999)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.bank_subject_count, group.count),
                    fontSize = 13.sp,
                    color = Color(0xFF999999)
                )
            }
        }

        // 删除按钮
        DeleteButton(
            text = stringResource(R.string.bank_delete),
            onClick = onDelete
        )
    }
}

private fun detectType(questions: List<Question>): String {
    if (questions.isEmpty()) return ""
    val sample = questions.first()
    return when {
        sample.answer == "正确" || sample.answer == "错误" -> "判断"
        sample.answer.length > 1 && sample.answer.all { it in 'A'..'Z' } -> "多选"
        else -> "单选"
    }
}

private fun formatDate(time: Long): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(time))
}
