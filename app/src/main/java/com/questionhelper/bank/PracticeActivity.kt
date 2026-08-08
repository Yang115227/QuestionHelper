package com.questionhelper.bank

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.questionhelper.QuestionApp
import com.questionhelper.data.Question
import com.questionhelper.data.QuestionRepository
import com.questionhelper.ui.theme.QuestionHelperTheme
import kotlinx.coroutines.launch

class PracticeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra("mode") ?: "single"
        val questionId = intent.getLongExtra("questionId", -1)
        val subject = intent.getStringExtra("subject") ?: "全部"
        setContent {
            QuestionHelperTheme {
                PracticeScreen(mode = mode, initialQuestionId = questionId, subject = subject)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(mode: String, initialQuestionId: Long, subject: String) {
    val context = LocalContext.current
    val repo = remember { QuestionRepository(QuestionApp.database.questionDao()) }
    val scope = rememberCoroutineScope()
    
    var questions by remember { mutableStateOf<List<Question>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    
    var showAnswer by remember { mutableStateOf(false) }
    var selectedOption by remember { mutableStateOf<String?>(null) }
    var isCorrect by remember { mutableStateOf<Boolean?>(null) }

    // 加载题目列表
    LaunchedEffect(mode, subject) {
        isLoading = true
        questions = when (mode) {
            "order" -> repo.getOrderedQuestions(subject)
            "random" -> repo.getRandomQuestions(subject)
            "wrong" -> emptyList() // 错题由下方的 LaunchedEffect 单独处理
            else -> {
                // single 模式
                val q = repo.getQuestionById(initialQuestionId)
                listOfNotNull(q)
            }
        }
        isLoading = false
        
        // 如果是单题模式，找到对应索引
        if (mode == "single" && initialQuestionId != -1L) {
            currentIndex = questions.indexOfFirst { it.id == initialQuestionId }.coerceAtLeast(0)
        }
    }

    // 错题模式单独处理
    LaunchedEffect(mode) {
        if (mode == "wrong") {
            isLoading = true
            val wrongList = mutableListOf<Question>()
            repo.wrongQuestions.collect {
                wrongList.clear()
                wrongList.addAll(it)
                questions = wrongList.toList()
                isLoading = false
            }
        }
    }

    val currentQuestion = questions.getOrNull(currentIndex)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        when (mode) {
                            "order" -> "顺序练习 (${currentIndex + 1}/${questions.size})"
                            "random" -> "随机练习 (${currentIndex + 1}/${questions.size})"
                            "wrong" -> "错题重做 (${currentIndex + 1}/${questions.size})"
                            else -> "题目详情"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { (context as? ComponentActivity)?.finish() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                questions.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Inbox,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("暂无题目", style = MaterialTheme.typography.titleMedium)
                        if (mode == "wrong") {
                            Text("错题本为空，快去练习吧！", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                currentQuestion != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        // 题目卡片
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "题目",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    currentQuestion.content,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                
                                // 选项
                                if (currentQuestion.options.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    val options = currentQuestion.options.split("|")
                                    options.forEachIndexed { index, opt ->
                                        val optLetter = ('A' + index).toString()
                                        val isSelected = selectedOption == optLetter
                                        val isAnswer = showAnswer && optLetter == currentQuestion.answer.trim()
                                        
                                        Card(
                                            onClick = {
                                                if (!showAnswer) {
                                                    selectedOption = optLetter
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = when {
                                                    isAnswer -> MaterialTheme.colorScheme.primaryContainer
                                                    isSelected && !showAnswer -> MaterialTheme.colorScheme.secondaryContainer
                                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                                }
                                            )
                                        ) {
                                            Text(
                                                opt.trim(),
                                                modifier = Modifier.padding(12.dp),
                                                color = when {
                                                    isAnswer -> MaterialTheme.colorScheme.primary
                                                    isSelected && showAnswer && !isAnswer -> MaterialTheme.colorScheme.error
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (!showAnswer) {
                            // 提交答案按钮
                            Button(
                                onClick = {
                                    if (selectedOption != null) {
                                        showAnswer = true
                                        isCorrect = selectedOption == currentQuestion.answer.trim()
                                        if (isCorrect == false) {
                                            scope.launch {
                                                repo.markWrong(currentQuestion.id)
                                            }
                                        }
                                    }
                                },
                                enabled = selectedOption != null,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Text("提交答案")
                            }
                        } else {
                            // 答案展示
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isCorrect == true) 
                                        MaterialTheme.colorScheme.primaryContainer 
                                    else 
                                        MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        if (isCorrect == true) "✓ 回答正确" else "✗ 回答错误",
                                        color = if (isCorrect == true) 
                                            MaterialTheme.colorScheme.primary 
                                        else 
                                            MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("正确答案：${currentQuestion.answer}")
                                }
                            }

                            if (currentQuestion.analysis.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("解析", style = MaterialTheme.typography.titleSmall)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(currentQuestion.analysis)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // 导航按钮
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (currentIndex > 0) {
                                            currentIndex--
                                            resetState()
                                        }
                                    },
                                    enabled = currentIndex > 0
                                ) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("上一题")
                                }

                                if (currentIndex < questions.size - 1) {
                                    Button(onClick = {
                                        currentIndex++
                                        resetState()
                                    }) {
                                        Text("下一题")
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.Default.ArrowForward, contentDescription = null)
                                    }
                                } else {
                                    Button(onClick = {
                                        (context as? ComponentActivity)?.finish()
                                    }) {
                                        Text("完成")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun resetState() {
    // 通过重组重置状态，这里不需要额外操作，因为currentIndex变化会触发重组
}