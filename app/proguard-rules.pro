# ============================================
# 基础保留规则
# ============================================

# 保留数据类（Room 实体、Parcelable 等）
-keep class com.questionhelper.data.** { *; }
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# 保留序列化相关注解
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# ============================================
# Jetpack Compose
# ============================================

# Compose 编译器生成的代码
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Compose Navigation
-keep class androidx.navigation.** { *; }
-keep class * implements androidx.navigation.NavArgs { *; }

# Material3 主题
-keep class com.questionhelper.ui.theme.** { *; }

# ============================================
# Room 数据库
# ============================================

-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keep class androidx.room.paging.** { *; }
-dontwarn androidx.room.paging.**

# Room 的 Kotlin 协程支持
-keepclassmembers class * {
    @androidx.room.Query <methods>;
    @androidx.room.Insert <methods>;
    @androidx.room.Update <methods>;
    @androidx.room.Delete <methods>;
}

# ============================================
# Kotlin 协程
# ============================================

-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ============================================
# CameraX
# ============================================

-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# ============================================
# ML Kit
# ============================================

-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class com.google.android.gms.internal.mlkit_vision_text.** { *; }

# ============================================
# Apache POI（Excel 解析）
# ============================================

-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-keep class org.openxmlformats.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.openxmlformats.**
-dontwarn com.microsoft.schemas.**
-dontwarn org.apache.commons.compress.**
-dontwarn javax.imageio.**
-dontwarn java.awt.**
-dontwarn org.apache.harmony.**

# ============================================
# Paddle Lite OCR
# ============================================

-keep class com.baidu.paddle.lite.** { *; }
-keep class com.baidu.paddle.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
# 保留 JNI 回调接口
-keep interface com.baidu.paddle.lite.** { *; }

# ============================================
# Kotlin 反射（如果使用了）
# ============================================

-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.reflect.**

# ============================================
# 日志和调试（可选：发布时移除）
# ============================================

# 保留行号信息以便崩溃时定位
-renamesourcefileattribute SourceFile
