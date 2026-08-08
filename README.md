# QuestionHelper 搜题助手

自用搜题APP，支持拍照搜题、悬浮搜题、题库导入与练习。

## 功能
- 📷 **拍照搜题**：CameraX + ML Kit OCR 本地识别
- 🔮 **悬浮搜题**：全局悬浮球，支持录屏/无障碍两种截图方式
- 📚 **题库管理**：支持 TXT/Excel 导入，顺序/错题练习
- 🔧 **GitHub Actions** 自动构建 Release APK

## 快速开始

### 本地构建
```bash
git clone <your-repo>
cd QuestionHelper

# 生成签名（只需一次）
keytool -genkey -v -keystore app/release.jks -alias questionhelper -keyalg RSA -keysize 2048 -validity 10000

# 构建
./gradlew assembleRelease
```

### GitHub Actions 自动构建
1. Fork 本仓库到 GitHub
2. 推送代码到 main 分支，自动触发构建
3. 在 Actions 页面下载 APK

## 权限说明
| 权限 | 用途 |
|------|------|
| 相机 | 拍照搜题 |
| 悬浮窗 | 悬浮球显示 |
| 录屏 | MediaProjection 截图 |
| 无障碍服务 | 安卓11+系统截图 |
| 存储读取 | 导入题库文件 |

## 题库格式

### TXT 格式
```
题干内容
A.选项1|B.选项2|C.选项3
答案：A
解析：这是解析内容
科目：数学
---
下一题...
```

### Excel 格式
| 内容 | 选项 | 答案 | 解析 | 科目 |
|------|------|------|------|------|
| 题干 | A.x\|B.y\|C.z | B | 解析内容 | 数学 |

## 技术栈
- Kotlin + Jetpack Compose
- CameraX + ML Kit Text Recognition
- Room Database
- Apache POI (Excel解析)
- MediaProjection / AccessibilityService

## 注意
- 本软件为自用工具，未针对应用商店审核做适配
- 悬浮搜题需要手动开启相关权限
- 首次使用需导入题库文件
