package com.questionhelper

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.questionhelper.bank.QuestionBankActivity
import com.questionhelper.search.CameraSearchActivity
import com.questionhelper.search.FloatWindowService
import com.questionhelper.search.ScreenCaptureService
import com.questionhelper.ui.theme.QuestionHelperTheme
import kotlinx.coroutines.launch

data class FeatureItem(
    val title: String,
    val icon: ImageVector,
    val description: String,
    val action: () -> Unit
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QuestionHelperTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainScreen() {
        val context = LocalContext.current
        var questionCount by remember { mutableStateOf(0) }
        var wrongCount by remember { mutableStateOf(0) }

        LaunchedEffect(Unit) {
            val repo = QuestionRepository(QuestionApp.database.questionDao())
            launch {
                questionCount = repo.getQuestionCount()
                wrongCount = repo.getWrongCount()
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("搜题助手", fontSize = 22.sp) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                // 统计卡片
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem("题库总数", "$questionCount", Icons.Default.MenuBook)
                        StatItem("错题数量", "$wrongCount", Icons.Default.Error)
                    }
                }

                Text(
                    text = "功能列表",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                val features = listOf(
                    FeatureItem(
                        "拍照搜题",
                        Icons.Default.CameraAlt,
                        "拍摄题目自动识别"
                    ) {
                        context.startActivity(Intent(context, CameraSearchActivity::class.java))
                    },
                    FeatureItem(
                        "悬浮搜题",
                        Icons.Default.Float,
                        "全局悬浮球快速搜题"
                    ) {
                        context.startService(Intent(context, FloatWindowService::class.java))
                    },
                    FeatureItem(
                        "我的题库",
                        Icons.Default.LibraryBooks,
                        "管理导入的题目"
                    ) {
                        context.startActivity(Intent(context, QuestionBankActivity::class.java))
                    },
                    FeatureItem(
                        "错题本",
                        Icons.Default.Bookmark,
                        "查看错题记录"
                    ) {
                        val intent = Intent(context, QuestionBankActivity::class.java)
                        intent.putExtra("mode", "wrong")
                        context.startActivity(intent)
                    },
                    FeatureItem(
                        "无障碍设置",
                        Icons.Default.Accessibility,
                        "开启无障碍服务"
                    ) {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    FeatureItem(
                        "录屏权限",
                        Icons.Default.ScreenShare,
                        "开启录屏截图权限"
                    ) {
                        ScreenCaptureService.requestPermission(context as ComponentActivity)
                    }
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(features) { feature ->
                        FeatureCard(feature)
                    }
                }
            }
        }
    }

    @Composable
    fun StatItem(label: String, value: String, icon: ImageVector) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    @Composable
    fun FeatureCard(feature: FeatureItem) {
        Card(
            onClick = feature.action,
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    feature.icon,
                    contentDescription = feature.title,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(feature.title, fontSize = 16.sp)
                Text(
                    feature.description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
