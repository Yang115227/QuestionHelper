package com.questionhelper.bank

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

class PracticeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra("mode") ?: "order"
        val subject = intent.getStringExtra("subject")?.takeIf { it.isNotBlank() } ?: "全部"
        val questionId = intent.getLongExtra("questionId", -1)
        setContent {
            QuestionHelperTheme {
                PracticeScreen(mode = mode, subject = subject, initialQuestionId = questionId)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(mode: String, subject: String, initialQuestionId: Long) {
    val context = LocalContext.current
    val repo = remember { QuestionRepository(QuestionApp.database.questionDao()) }
    val scope = rememberCoroutineScope()
    
    var questions by remember { mutableStateOf<List<Question>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var showAnswer by remember { mutableStateOf(false) }
    var selectedOptions by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isCorrect by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(mode, subject) {
        isLoading = true
        questions = try {
            when (mode) {
                "order" -> repo.getOrderedQuestions(subject)
                "random" -> repo.getRandomQuestions(subject)
                "single" -> listOfNotNull(repo.getQuestionById(initialQuestionId))
                else -> repo.getOrderedQuestions(subject)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
        isLoading = false
        currentIndex = 0
        showAnswer = false
        selectedOptions = emptySet()
        isCorrect = null
    }

    val currentQuestion = questions.getOrNull(currentIndex)
    val isMultiSelect = currentQuestion?.answer?.length?.let { it > 1 } ?: false
    val isJudge = currentQuestion?.answer == "正确" || currentQuestion?.answer == "错误"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${subject} (${currentIndex + 1}/${questions.size})", fontSize = 17.sp) },
                navigationIcon = {
                    IconButton(onClick = { (context as? ComponentActivity)?.finish() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                questions.isEmpty() -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("暂无题目", style = MaterialTheme.typography.titleMedium)
                }
                currentQuestion != null -> {
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        // 题目
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("${currentIndex + 1}. ${currentQuestion.content}", fontSize = 16.sp, lineHeight = 24.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 判断题：显示正确/错误选项
                        if (isJudge) {
                            listOf("正确", "错误").forEach { opt ->
                                val isSelected = selectedOptions.contains(opt)
                                val isAnswer = showAnswer && opt == currentQuestion.answer

                                Card(
                                    onClick = { if (!showAnswer) selectedOptions = setOf(opt) },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = when {
                                            isAnswer -> Color(0xFFE8F5E9)
                                            isSelected && !showAnswer -> Color(0xFFE3F2FD)
                                            isSelected && showAnswer && !isAnswer -> Color(0xFFFFEBEE)
                                            else -> Color.White
                                        }
                                    ),
                                    border = if (isSelected || isAnswer) BorderStroke(
                                        1.5.dp,
                                        when {
                                            isAnswer -> Color(0xFF4CAF50)
                                            isSelected && !showAnswer -> Color(0xFF2196F3)
                                            else -> Color(0xFFF44336)
                                        }
                                    ) else null
                                ) {
                                    Text(
                                        opt,
                                        modifier = Modifier.padding(14.dp),
                                        fontSize = 16.sp,
                                        color = when {
                                            isAnswer -> Color(0xFF4CAF50)
                                            isSelected && showAnswer && !isAnswer -> Color(0xFFF44336)
                                            isSelected -> Color(0xFF2196F3)
                                            else -> Color(0xFF212121)
                                        }
                                    )
                                }
                            }
                        }
                        // 有选项的题目（单选/多选）
                        else if (currentQuestion.options.isNotEmpty()) {
                            val options = currentQuestion.options.split("|")
                            options.forEach { opt ->
                                val optLetter = opt.substringBefore(".").trim()
                                val optText = opt.substringAfter(".").trim()
                                val isSelected = selectedOptions.contains(optLetter)
                                val isAnswer = showAnswer && currentQuestion.answer.trim().contains(optLetter)

                                Card(
                                    onClick = {
                                        if (!showAnswer) {
                                            selectedOptions = if (isMultiSelect) {
                                                // 多选：切换选中状态
                                                if (isSelected) selectedOptions - optLetter else selectedOptions + optLetter
                                            } else {
                                                // 单选：替换
                                                setOf(optLetter)
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = when {
                                            isAnswer -> Color(0xFFE8F5E9)
                                            isSelected && !showAnswer -> Color(0xFFE3F2FD)
                                            isSelected && showAnswer && !isAnswer -> Color(0xFFFFEBEE)
                                            else -> Color.White
                                        }
                                    ),
                                    border = if (isSelected || isAnswer) BorderStroke(
                                        1.5.dp,
                                        when {
                                            isAnswer -> Color(0xFF4CAF50)
                                            isSelected && !showAnswer -> Color(0xFF2196F3)
                                            else -> Color(0xFFF44336)
                                        }
                                    ) else null
                                ) {
                                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "$optLetter.",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = when {
                                                isAnswer -> Color(0xFF4CAF50)
                                                isSelected && showAnswer && !isAnswer -> Color(0xFFF44336)
                                                isSelected -> Color(0xFF2196F3)
                                                else -> Color(0xFF212121)
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            optText,
                                            fontSize = 15.sp,
                                            color = when {
                                                isAnswer -> Color(0xFF4CAF50)
                                                isSelected && showAnswer && !isAnswer -> Color(0xFFF44336)
                                                else -> Color(0xFF424242)
                                            }
                                        )
                                    }
                                }
                            }
                        } else {
                            // 填空题
                            OutlinedTextField(
                                value = selectedOptions.firstOrNull() ?: "",
                                onValueChange = { if (!showAnswer) selectedOptions = setOf(it) },
                                label = { Text("请输入答案") },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !showAnswer
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        if (!showAnswer) {
                            Button(
                                onClick = {
                                    if (selectedOptions.isNotEmpty()) {
                                        showAnswer = true
                                        // 多选判断：排序后比较
                                        val userAns = selectedOptions.sorted().joinToString("")
                                        val correctAns = currentQuestion.answer.trim().toList().sorted().joinToString("")
                                        isCorrect = userAns == correctAns
                                        if (isCorrect == false) {
                                            scope.launch { repo.markWrong(currentQuestion.id) }
                                        }
                                    }
                                },
                                enabled = selectedOptions.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Text("提交答案", fontSize = 16.sp)
                            }
                        } else {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isCorrect == true) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        if (isCorrect == true) "✓ 回答正确" else "✗ 回答错误",
                                        color = if (isCorrect == true) Color(0xFF4CAF50) else Color(0xFFF44336),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("正确答案：${currentQuestion.answer}", fontSize = 15.sp)
                                }
                            }

                            if (currentQuestion.analysis.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("解析", fontWeight = FontWeight.Medium)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(currentQuestion.analysis, fontSize = 14.sp, lineHeight = 20.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                OutlinedButton(
                                    onClick = {
                                        if (currentIndex > 0) {
                                            currentIndex--
                                            showAnswer = false
                                            selectedOptions = emptySet()
                                            isCorrect = null
                                        }
                                    },
                                    enabled = currentIndex > 0
                                ) { Text("上一题") }

                                if (currentIndex < questions.size - 1) {
                                    Button(onClick = {
                                        currentIndex++
                                        showAnswer = false
                                        selectedOptions = emptySet()
                                        isCorrect = null
                                    }) { Text("下一题") }
                                } else {
                                    Button(onClick = { (context as? ComponentActivity)?.finish() }) { Text("完成") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}