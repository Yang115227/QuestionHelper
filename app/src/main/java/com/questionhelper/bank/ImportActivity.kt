package com.questionhelper.bank

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.questionhelper.QuestionApp
import com.questionhelper.data.Question
import com.questionhelper.data.QuestionRepository
import com.questionhelper.parser.ExcelParser
import com.questionhelper.parser.TxtParser
import com.questionhelper.ui.theme.QuestionHelperTheme
import kotlinx.coroutines.launch
import java.io.InputStream

class ImportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QuestionHelperTheme {
                ImportScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImportScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { QuestionRepository(QuestionApp.database.questionDao()) }
    var importStatus by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("默认") }
    var customCategory by remember { mutableStateOf("") }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var existingSubjects by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        repo.allSubjects.collect { existingSubjects = it }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            isImporting = true
            scope.launch {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val fileName = getFileName(context, uri)
                    val category = if (customCategory.isNotBlank()) customCategory else selectedCategory
                    val questions = parseFile(inputStream, fileName, category)
                    repo.insertQuestions(questions)
                    importStatus = "成功导入 ${questions.size} 道题目（分类：$category）"
                    customCategory = ""
                } catch (e: Exception) {
                    importStatus = "导入失败: ${e.message}"
                } finally {
                    isImporting = false
                }
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("导入题库") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 分类选择
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showCategoryDialog = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("导入分类", style = MaterialTheme.typography.labelMedium)
                        Text(
                            if (customCategory.isNotBlank()) customCategory else selectedCategory,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "选择分类")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "支持格式：TXT、Excel（.xlsx）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { filePicker.launch("*/*") },
                enabled = !isImporting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("选择文件导入")
            }

            if (isImporting) {
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator()
            }

            if (importStatus.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    importStatus,
                    color = if (importStatus.startsWith("成功"))
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 格式说明
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("TXT格式说明", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("题干内容")
                    Text("A.选项1|B.选项2|C.选项3")
                    Text("答案：A")
                    Text("解析：xxx")
                    Text("---（分隔线）")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "注：如不指定分类，将使用上方选择的分类",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // 分类选择对话框
    if (showCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showCategoryDialog = false },
            title = { Text("选择导入分类") },
            text = {
                Column {
                    Text(
                        "选择已有分类或输入新分类：",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    // 现有分类
                    if (existingSubjects.isNotEmpty()) {
                        Text("已有分类：", style = MaterialTheme.typography.labelMedium)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            existingSubjects.forEach { subject ->
                                FilterChip(
                                    selected = selectedCategory == subject && customCategory.isBlank(),
                                    onClick = {
                                        selectedCategory = subject
                                        customCategory = ""
                                    },
                                    label = { Text(subject) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    
                    // 新分类输入
                    OutlinedTextField(
                        value = customCategory,
                        onValueChange = { customCategory = it },
                        label = { Text("新分类名称") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showCategoryDialog = false }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    customCategory = ""
                    showCategoryDialog = false 
                }) {
                    Text("使用默认")
                }
            }
        )
    }
}

private fun parseFile(inputStream: InputStream?, fileName: String?, defaultCategory: String): List<Question> {
    inputStream ?: return emptyList()
    return when {
        fileName?.endsWith(".xlsx") == true || fileName?.endsWith(".xls") == true -> {
            ExcelParser.parse(inputStream, defaultCategory)
        }
        else -> TxtParser.parse(inputStream, defaultCategory)
    }
}

private fun getFileName(context: Context, uri: Uri): String? {
    var name: String? = null
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && nameIndex >= 0) {
            name = cursor.getString(nameIndex)
        }
    }
    return name
}