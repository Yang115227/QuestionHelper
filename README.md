# QuestionHelper 搜题助手

一个基于 Android 的本地搜题工具，支持拍照搜题、悬浮球截图搜题、题库导入与练习。所有 OCR 识别均在本地完成，无需联网。

<p align="center">
  <img src="https://img.shields.io/badge/Android-8.0%2B-brightgreen" alt="Android 8.0+">
  <img src="https://img.shields.io/badge/Kotlin-1.9-blue" alt="Kotlin">
  <img src="https://img.shields.io/badge/Jetpack%20Compose-UI-purple" alt="Compose">
  <img src="https://img.shields.io/badge/PaddleOCR-Local-orange" alt="PaddleOCR">
</p>

---

## 功能特性

| 功能 | 说明 |
|------|------|
| 📷 **拍照搜题** | CameraX 实时预览，框选题目区域，PaddleOCR 本地识别 |
| 🔮 **悬浮搜题** | 全局悬浮球，支持**录屏截图**和**无障碍截图**两种模式，框选即搜 |
| 📝 **悬浮结果窗** | 搜索结果以可拖拽悬浮窗展示，匹配成功标红，15秒自动关闭 |
| 📚 **题库管理** | Room 数据库存储，支持 TXT / Excel 导入，按科目分类 |
| 🎯 **练习模式** | 顺序练习、错题回顾，答题后立即显示正误与解析 |
| 🔧 **CI 自动构建** | GitHub Actions 自动编译 Release APK |

---

## 快速开始

### 方式一：下载 Release APK

在 [Actions](https://github.com/Yang115227/QuestionHelper/actions) 页面选择最新成功的构建，下载 `app-release` 或 `app-debug`。

### 方式二：本地构建

```bash
git clone https://github.com/Yang115227/QuestionHelper.git
cd QuestionHelper

# 可选：生成签名（Release 需要）
keytool -genkey -v -keystore app/release.jks \
  -alias questionhelper -keyalg RSA -keysize 2048 -validity 10000

# 构建 Debug APK
./gradlew assembleDebug

# 构建 Release APK
./gradlew assembleRelease
方式三：GitHub Actions 自动构建
1. 
Fork 本仓库
2. 
在仓库 Settings → Secrets and variables → Actions 中添加签名密钥：
 
 SIGNING_STORE_FILE ：Base64 编码的  release.jks  文件
 
 SIGNING_STORE_PASSWORD ：密钥库密码
 
 SIGNING_KEY_ALIAS ：密钥别名
 
 SIGNING_KEY_PASSWORD ：密钥密码
3. 
推送代码到  main  或  trae/agent-QQOeMZ  分支，自动触发构建
4. 
在 Actions 页面下载 APK
使用指南
1. 导入题库
首次使用需导入题库文件：
TXT 格式
题干内容
A.选项1|B.选项2|C.选项3
答案：A
解析：这是解析内容
科目：数学
---
下一题...
Excel 格式
内容	选项	答案	解析	科目	
题干	A.x\|B.y\|C.z	B	解析内容	数学	
进入 题库 → 导入，选择文件即可批量导入。
2. 拍照搜题
进入 拍照搜题，CameraX 实时预览，框选题目区域后自动 OCR 识别并匹配题库。
3. 悬浮搜题
1. 
授予悬浮窗权限
2. 
选择截图方式：
 
录屏搜题：授予 MediaProjection 录屏权限，适用于所有 Android 版本
 
无障碍搜题：开启无障碍服务（Android 11+ 系统截图，更稳定）
3. 
点击悬浮球 → 框选题目区域 → 确认搜题
4. 
结果以可拖拽悬浮窗展示，匹配成功答案标红
权限说明
权限	用途	
`CAMERA`	拍照搜题	
`SYSTEM_ALERT_WINDOW`	悬浮球 / 结果窗显示	
`FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MEDIA_PROJECTION`	录屏截图服务	
`BIND_ACCESSIBILITY_SERVICE`	无障碍截图（Android 11+）	
`READ_EXTERNAL_STORAGE`	导入题库文件	
技术栈
 
UI：Kotlin + Jetpack Compose + Material Design 3
 
OCR：Paddle Lite（PP-OCRv3）本地推理，无需联网
 
数据库：Room + SQLite
 
相机：CameraX
 
Excel 解析：Apache POI
 
截图：MediaProjection（录屏）/ AccessibilityService（无障碍）
 
构建：Gradle + GitHub Actions
项目结构
app/src/main/java/com/questionhelper/
├── MainActivity.kt              # 主界面（底部导航）
├── QuestionApp.kt               # Application，预初始化 OCR
├── bank/                        # 题库与练习
│   ├── QuestionBankActivity.kt
│   ├── PracticeActivity.kt
│   └── ImportActivity.kt
├── data/                        # 数据层
│   ├── AppDatabase.kt
│   ├── QuestionDao.kt
│   └── QuestionRepository.kt
├── ocr/                         # OCR 引擎
│   ├── OcrManager.kt            # OCR 管理器（PaddleOCR）
│   ├── OCRPredictor.kt          # 推理封装
│   └── PaddleLiteManager.java   # 原生接口
├── search/                      # 搜题核心
│   ├── CameraSearchActivity.kt  # 拍照搜题
│   ├── FloatWindowService.kt    # 悬浮球服务
│   ├── FloatResultView.kt       # 悬浮结果窗
│   ├── ScreenCaptureService.kt  # 录屏截图服务
│   ├── AccessibilitySearchService.kt  # 无障碍截图服务
│   └── CropOverlayView.kt       # 框选层
└── search/ui/                   # 搜题相关 UI
注意事项
1. 
OCR 模型：首次安装后无需下载模型，PP-OCRv3 模型已打包在 APK  assets/paddleocr/  中
2. 
Paddle Lite 库： app/libs/PaddlePredictor.jar  和  app/src/main/jniLibs/arm64-v8a/*.so  已提交到仓库，CI 直接复用
3. 
架构支持：仅支持  arm64-v8a  设备
4. 
悬浮搜题权限：部分国产 ROM（MIUI/ColorOS 等）需要在系统设置中额外开启"后台弹出界面"权限
5. 
题库匹配：采用模糊匹配，OCR 识别结果与题库内容前 50 个字符比对
License
MIT License
本工具仅供学习交流使用，请遵守相关法律法规。