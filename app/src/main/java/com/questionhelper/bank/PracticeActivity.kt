package com.questionhelper.bank

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.questionhelper.QuestionApp
import com.questionhelper.data.QuestionRepository
import com.questionhelper.ui.theme.QuestionHelperTheme
import kotlinx.coroutines.launch

class PracticeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val questionId = intent.getLongExtra("questionId", -1)
        setContent {
            QuestionHelperTheme {
                PracticeScreen(questionId = questionId)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(questionId: Long) {
    val repo = remember { QuestionRepository(QuestionApp.database.questionDao()) }
    var question by remember { mutableStateOf<com.questionhelper.data.Question?>(null) }
    var showAnswer by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(questionId) {
        question = repo.getQuestionById(questionId)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("练习模式") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            question?.let { q ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("题目", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(q.content, style = MaterialTheme.typography.bodyLarge)
                        if (q.options.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            q.options.split("|").forEach { opt ->
                                Text(opt.trim(), modifier = Modifier.padding(vertical = 2.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (!showAnswer) {
                    Button(
                        onClick = { showAnswer = true },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("查看答案")
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("答案", color = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(q.answer, style = MaterialTheme.typography.headlineSmall)
                        }
                    }

                    if (q.analysis.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("解析")
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(q.analysis)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = { showAnswer = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("下一题")
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    repo.markWrong(q.id)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("加入错题")
                        }
                    }
                }
            } ?: run {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}
