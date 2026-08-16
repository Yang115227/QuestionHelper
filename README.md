# 📚 搜题助手 (QuestionHelper)

一个基于 Android 的本地智能搜题应用，支持拍照搜题、悬浮窗截图搜题、题库管理与刷题练习等功能。所有数据存储在本地，无需联网即可使用。

---

## ✨ 功能特性

| 功能 | 描述 |
|------|------|
| 📷 **拍照搜题** | 使用相机拍摄题目，通过 OCR 自动识别文字并在本地题库中搜索匹配答案 |
| 🔮 **悬浮窗搜题** | 全局悬浮球，支持录屏截图 / 无障碍截图两种模式，快速识别屏幕上的题目 |
| 📂 **题库管理** | 支持导入 Excel / TXT 格式的题库文件，按科目分类管理 |
| 📝 **刷题练习** | 选择题库进行顺序或随机练习，实时记录答题情况 |
| ❌ **错题本** | 自动收录练习中的错题，支持单独复习 |

---

## 🛠 技术栈

- **语言**: Kotlin
- **UI 框架**: Jetpack Compose (Material 3)
- **数据库**: Room (SQLite)
- **相机**: CameraX
- **OCR 识别**: Google ML Kit (中文文字识别)
- **截图方案**: 
  - MediaProjection API (录屏截图，Android 5+)
  - AccessibilityService (无障碍截图，Android 11+)
- **文件解析**: Apache POI (Excel)、自定义解析器 (TXT)
- **最低系统**: Android 8.0 (API 26)
- **目标系统**: Android 14 (API 34)

---

## 📁 项目结构

```
app/src/main/java/com/questionhelper/
├── MainActivity.kt              # 主界面（功能入口）
├── QuestionApp.kt               # Application 入口
├── bank/
│   ├── ImportActivity.kt        # 题库导入
│   ├── PracticeActivity.kt      # 刷题练习
│   ├── PracticeSelectActivity.kt # 练习科目选择
│   ├── QuestionBankActivity.kt  # 题库管理
│   └── WrongBookScreen.kt       # 错题本
├── data/
│   ├── AppDatabase.kt           # Room 数据库
│   ├── Question.kt              # 题目数据实体
│   ├── QuestionDao.kt           # 数据访问对象
│   └── QuestionRepository.kt    # 数据仓库
├── ocr/
│   └── OcrManager.kt            # ML Kit OCR 封装
├── parser/
│   ├── ExcelParser.kt           # Excel 题库解析
│   └── TxtParser.kt             # TXT 题库解析
├── search/
│   ├── AccessibilitySearchService.kt  # 无障碍截图搜索服务
│   ├── CameraSearchActivity.kt        # 拍照搜索界面
│   ├── CropOverlayView.kt             # 截图裁剪覆盖层
│   ├── FloatResultView.kt             # 悬浮搜索结果展示
│   ├── FloatWindowService.kt          # 悬浮窗服务
│   ├── ScreenCaptureService.kt        # 录屏截图服务
│   └── SearchResultActivity.kt        # 搜索结果展示
└── ui/
    ├── components/
    │   └── CommonComponents.kt  # 通用 UI 组件
    └── theme/
        └── Theme.kt             # 应用主题
```

---

## 🚀 快速开始

### 环境要求

- Android Studio Hedgehog 或更高版本
- JDK 17
- Android SDK 34
- Gradle 8.x

### 构建步骤

1. **克隆仓库**
   ```bash
   git clone https://github.com/Yang115227/QuestionHelper.git
   cd QuestionHelper
   ```

2. **使用 Android Studio 打开项目**

   选择 `settings.gradle.kts` 所在的根目录。

3. **同步 Gradle**

   点击 **File → Sync Project with Gradle Files**，等待依赖下载完成。

4. **运行应用**

   连接 Android 设备（API 26+）或启动模拟器，点击 **Run**。

> **注意**: 项目包含 ML Kit 模型，首次运行时会自动下载 OCR 中文识别模型。

---

## 📖 使用说明

### 1. 导入题库

应用首次使用时题库为空，需要先导入题库文件：

- **Excel 格式**: 支持 `.xls` / `.xlsx`，要求包含题目、选项、答案、解析、科目等列
- **TXT 格式**: 自定义分隔符格式，具体格式参考 `TxtParser.kt`

进入 **我的题库 → 导入文件** 选择题库文件即可。

### 2. 拍照搜题

点击主界面 **拍照搜题**，授权相机权限后：
1. 对准题目拍照
2. 应用自动进行 OCR 文字识别
3. 在本地题库中模糊匹配题目内容
4. 展示匹配到的答案与解析

### 3. 悬浮窗搜题

点击主界面 **悬浮搜题**，按提示开启必要权限：

| 权限 | 用途 | 系统要求 |
|------|------|----------|
| 悬浮窗权限 | 显示全局悬浮球 | Android 8+ |
| 录屏授权 | MediaProjection 截图 | Android 5+ |
| 无障碍服务 | AccessibilityService 截图 | Android 11+ |

**使用方式**：
- 点击悬浮球 → 选择截图区域 → OCR 识别 → 展示搜索结果
- 支持在任意 App 上方使用（网课、PDF 阅读器等）

### 4. 刷题练习

进入 **我的题库 → 选择科目 → 开始练习**：
- 支持顺序练习、随机练习
- 答错自动加入错题本
- 实时显示正确率统计

---

## 🔐 权限说明

应用需要以下权限才能正常使用：

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
```

> 所有数据均在本地处理，不涉及网络上传，保护隐私。

---

## 📦 主要依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| AndroidX Compose BOM | Latest | UI 构建 |
| CameraX | 1.3.x | 相机预览与拍照 |
| Room | 2.6.x | 本地数据库 |
| ML Kit Text Recognition (Chinese) | 16.0.1 | 中文 OCR |
| Apache POI | 5.x | Excel 文件解析 |
| Material Components | 1.12.0 | Material Design 组件 |

完整依赖列表请查看 [`app/build.gradle.kts`](app/build.gradle.kts)。

---

## 📝 题库格式示例

### Excel 格式

| 题目 | 选项A | 选项B | 选项C | 选项D | 答案 | 解析 | 科目 |
|------|-------|-------|-------|-------|------|------|------|
| 地球绕什么公转？ | 月球 | 太阳 | 火星 | 金星 | B | 地球围绕太阳公转 | 地理 |

### TXT 格式

```
[题目]地球绕什么公转？
[A]月球
[B]太阳
[C]火星
[D]金星
[答案]B
[解析]地球围绕太阳公转
[科目]地理
```

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

1. Fork 本仓库
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

---

## 📄 许可证

本项目采用 [MIT License](LICENSE) 开源协议。

---

## 🙏 致谢

- [Google ML Kit](https://developers.google.com/ml-kit) - 提供强大的端侧 OCR 能力
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - 现代化的 Android UI 工具包
- [Apache POI](https://poi.apache.org/) - Java Excel 处理库

---

> 如果本项目对你有帮助，欢迎 ⭐ Star 支持！
