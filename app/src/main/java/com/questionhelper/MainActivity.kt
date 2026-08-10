package com.questionhelper

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import com.questionhelper.bank.QuestionBankActivity
import com.questionhelper.data.QuestionRepository
import com.questionhelper.search.AccessibilitySearchService
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
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var screenCaptureLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        screenCaptureLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                startScreenCaptureService(result.resultCode, result.data!!)
            } else {
                Toast.makeText(this, "录屏权限被拒绝", Toast.LENGTH_SHORT).show()
            }
        }

        val showCaptureChoice = intent.getBooleanExtra("show_capture_choice", false)
        setContent {
            QuestionHelperTheme {
                MainScreen(
                    showCaptureChoice = showCaptureChoice,
                    onRequestMediaProjection = { requestMediaProjection() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkServiceStateAndStartFloatWindow()
    }

    private fun checkServiceStateAndStartFloatWindow() {
        val hasOverlayPermission = FloatWindowService.checkPermission(this)
        val hasAccessibility = isAccessibilityServiceEnabled()
        val hasScreenCapture = ScreenCaptureService.isRunning && ScreenCaptureService.isInitialized

        if (hasOverlayPermission && (hasAccessibility || hasScreenCapture)) {
            FloatWindowService.start(this)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponent = ComponentName(this, AccessibilitySearchService::class.java)
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(expectedComponent.flattenToString())
    }

    private fun requestMediaProjection() {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        try {
            screenCaptureLauncher.launch(manager.createScreenCaptureIntent())
        } catch (e: Exception) {
            Log.e("MainActivity", "Launch screen capture intent failed", e)
            Toast.makeText(this, "无法启动录屏授权：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun startScreenCaptureService(resultCode: Int, data: Intent) {
        val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
            putExtra("result_code", resultCode)
            putExtra("result_data", data)
        }
        try {
            ContextCompat.startForegroundService(this, serviceIntent)
            Toast.makeText(this, "录屏服务已启动", Toast.LENGTH_SHORT).show()
            FloatWindowService.start(this)
        } catch (e: Exception) {
            Log.e("MainActivity", "Start screen capture service failed", e)
            Toast.makeText(this, "录屏服务启动失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    showCaptureChoice: Boolean = false,
    onRequestMediaProjection: () -> Unit = {}
) {
    val context = LocalContext.current
    var questionCount by remember { mutableStateOf(0) }
    var wrongCount by remember { mutableStateOf(0) }
    var showCaptureDialog by remember { mutableStateOf(showCaptureChoice) }

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
                FeatureItem("拍照搜题", Icons.Default.CameraAlt, "拍摄题目自动识别") {
                    context.startActivity(Intent(context, CameraSearchActivity::class.java))
                },
                FeatureItem("悬浮搜题", Icons.Default.TouchApp, "全局悬浮球快速搜题") {
                    if (!FloatWindowService.checkPermission(context)) {
                        Toast.makeText(context, "请先开启悬浮窗权限", Toast.LENGTH_LONG).show()
                        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                        return@FeatureItem
                    }

                    val hasScreenCapture = ScreenCaptureService.isRunning && ScreenCaptureService.isInitialized
                    val hasAccessibility = isAccessibilityEnabled(context)

                    if (!hasScreenCapture && !hasAccessibility) {
                        showCaptureDialog = true
                    } else {
                        FloatWindowService.start(context)
                        Toast.makeText(context, "悬浮球已显示", Toast.LENGTH_SHORT).show()
                    }
                },
                FeatureItem("我的题库", Icons.Default.LibraryBooks, "管理导入的题目") {
                    context.startActivity(Intent(context, QuestionBankActivity::class.java))
                },
                FeatureItem("错题本", Icons.Default.Bookmark, "查看错题记录") {
                    val intent = Intent(context, QuestionBankActivity::class.java)
                    intent.putExtra("mode", "wrong")
                    context.startActivity(intent)
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

    if (showCaptureDialog) {
        AlertDialog(
            onDismissRequest = { showCaptureDialog = false },
            title = { Text("选择截图方式") },
            text = {
                Column {
                    Text("悬浮搜题需要截图能力，请选择一种方式：")
                    Spacer(modifier = Modifier.height(12.dp))

                    Card(
                        onClick = {
                            showCaptureDialog = false
                            onRequestMediaProjection()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ScreenShare, contentDescription = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("录屏搜题", style = MaterialTheme.typography.titleSmall)
                                Text("兼容性好，支持所有安卓版本", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        onClick = {
                            showCaptureDialog = false
                            Toast.makeText(context, "请开启搜题助手的无障碍服务", Toast.LENGTH_LONG).show()
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Accessibility, contentDescription = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("无障碍搜题", style = MaterialTheme.typography.titleSmall)
                                Text("仅安卓11+，权限更轻量", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCaptureDialog = false }) {
                    Text("取消")
                }
            }
        )
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

private fun isAccessibilityEnabled(context: Context): Boolean {
    return try {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        enabledServices.contains("com.questionhelper/.search.AccessibilitySearchService")
    } catch (e: Exception) {
        false
    }
}