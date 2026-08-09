package com.questionhelper.bank

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.questionhelper.QuestionApp
import com.questionhelper.data.Question
import com.questionhelper.data.QuestionRepository
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

@OptIn(ExperimentalMaterial3Api::class)
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
            TopAppBar(
                title = { Text("我的题库", fontSize = 18.sp, fontWeight = FontWeight.Medium) },
                navigationIcon = {
                    IconButton(onClick = { (context as? ComponentActivity)?.finish() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 导入入口
                    IconButton(onClick = {
                        context.startActivity(Intent(context, ImportActivity::class.java))
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "导入题库")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
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
                Divider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
            }
        }

        if (subjectGroups.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("暂无导入的题库", color = Color(0xFF999999), fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("点击右上角 + 导入Excel文件", color = Color(0xFFBBBBBB), fontSize = 13.sp)
                }
            }
        }
    }

    // 删除确认
    showDeleteDialog?.let { group ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("确认删除") },
            text = { Text("确定删除「${group.subject}」吗？共 ${group.count} 道题，此操作不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repo.deleteQuestionsBySubject(group.subject)
                        }
                        showDeleteDialog = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFF44336))
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("取消") }
            }
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
                color = Color(0xFF212121)
            )
            if (group.typeLabel.isNotEmpty()) {
                Text(
                    text = group.typeLabel,
                    fontSize = 14.sp,
                    color = Color(0xFF757575),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Row(
                modifier = Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "更新时间：${formatDate(group.updateTime)}",
                    fontSize = 13.sp,
                    color = Color(0xFF999999)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "数量：${group.count}",
                    fontSize = 13.sp,
                    color = Color(0xFF999999)
                )
            }
        }

        // 删除按钮
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFFCCCCCC))
                .clickable(onClick = onDelete)
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            Text(
                text = "删除",
                fontSize = 14.sp,
                color = Color.White
            )
        }
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

