package com.questionhelper.bank

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.questionhelper.data.QuestionRepository
import com.questionhelper.ui.theme.QuestionHelperTheme
import kotlinx.coroutines.launch

class QuestionBankActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QuestionHelperTheme {
                QuestionBankScreen()
            }
        }
    }
}

data class SubjectItem(
    val name: String,
    val count: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionBankScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { QuestionRepository(QuestionApp.database.questionDao()) }
    
    var subjects by remember { mutableStateOf<List<SubjectItem>>(emptyList()) }
    var selectedSubject by remember { mutableStateOf<SubjectItem?>(null) }
    var practiceMode by remember { mutableStateOf("order") } // "order" 或 "random"

    // 监听题目数据变化，更新分类列表
    val allQuestions by repo.allQuestions.collectAsState(initial = emptyList())

    LaunchedEffect(allQuestions) {
        val grouped = allQuestions.groupBy { it.subject }
        subjects = grouped.map { (name, list) ->
            SubjectItem(name, list.size)
        }.sortedByDescending { it.count }
        if (subjects.isNotEmpty() && selectedSubject == null) {
            selectedSubject = subjects.first()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "题库练习",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { 
                        (context as? ComponentActivity)?.finish() 
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        bottomBar = {
            Column {
                // 顺序/随机切换
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ModeTab(
                        text = "顺序练习",
                        selected = practiceMode == "order",
                        onClick = { practiceMode = "order" }
                    )
                    ModeTab(
                        text = "随机练习",
                        selected = practiceMode == "random",
                        onClick = { practiceMode = "random" }
                    )
                }
                
                // 开始练习按钮
                Button(
                    onClick = {
                        selectedSubject?.let { subject ->
                            val intent = Intent(context, PracticeActivity::class.java).apply {
                                putExtra("mode", practiceMode)
                                putExtra("subject", subject.name)
                            }
                            context.startActivity(intent)
                        }
                    },
                    enabled = selectedSubject != null && selectedSubject!!.count > 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2196F3),
                        disabledContainerColor = Color(0xFFBBDEFB)
                    )
                ) {
                    Text(
                        "开始练习",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(subjects, key = { it.name }) { item ->
                SubjectCard(
                    item = item,
                    isSelected = selectedSubject?.name == item.name,
                    onClick = { selectedSubject = item }
                )
            }
        }
    }
}

@Composable
fun SubjectCard(
    item: SubjectItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) Color(0xFF2196F3) else Color(0xFFE0E0E0)
    val backgroundColor = if (isSelected) Color(0xFFE3F2FD) else Color.White

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(1.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(backgroundColor)
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.name,
                    fontSize = 17.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    color = Color(0xFF212121)
                )
                Text(
                    text = "${item.count}道",
                    fontSize = 15.sp,
                    color = if (isSelected) Color(0xFF2196F3) else Color(0xFF9E9E9E)
                )
            }
        }
    }
}

@Composable
fun ModeTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Color(0xFFE3F2FD) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            color = if (selected) Color(0xFF2196F3) else Color(0xFF757575),
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
        )
    }
}