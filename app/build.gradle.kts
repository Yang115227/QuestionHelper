plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.questionhelper"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.questionhelper"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        create("release") {
            // 优先从环境变量读取（CI 构建）
            // 本地构建时如果没有设置环境变量，使用 debug 签名（避免硬编码密码）
            val storeFilePath = System.getenv("STORE_FILE") ?: ""
            val storePw = System.getenv("STORE_PASSWORD")
            val keyAliasName = System.getenv("KEY_ALIAS")
            val keyPw = System.getenv("KEY_PASSWORD")

            if (storeFilePath.isNotEmpty() && storePw != null && keyAliasName != null && keyPw != null) {
                storeFile = file(storeFilePath)
                storePassword = storePw
                keyAlias = keyAliasName
                keyPassword = keyPw
            } else {
                // 本地开发或 CI 没有配置签名时，使用 debug 签名
                // 这样不会生成"正式签名"的 release 包，避免密钥泄露
                println("Warning: Release signing config not fully set, falling back to debug signing")
                // 不设置 storeFile 等属性，Gradle 会自动使用 debug 签名
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (signingConfigs.getByName("release").storeFile != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            // Use default debug keystore
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }

    androidResources {
        noCompress += listOf("tflite", "lite", "nb", "txt")
    }

    sourceSets["main"].java {
        // 没有真实 PaddlePredictor.jar 时，使用 stub 类参与编译，保证项目可构建
        if (!file("libs/PaddlePredictor.jar").exists()) {
            srcDir("src/stub/java")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation)
    implementation(libs.coroutines.android)

    // Android Material Components（XML Theme.Material3 需要）
    implementation("com.google.android.material:material:1.12.0")

    // CameraX
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    // ML Kit (主 OCR 方案，无需额外模型文件)
    implementation(libs.mlkit.text)
    implementation(libs.mlkit.text.chinese)
    // 强制统一内部依赖版本，避免 Google Play 服务内部模型下载冲突
    constraints {
        implementation("com.google.android.gms:play-services-mlkit-text-recognition-common:19.0.0") {
            because("统一 ML Kit 内部公共库版本，避免中文识别包与主包冲突")
        }
    }

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // POI (Excel 解析) —— 排除 Android 不兼容/重复的依赖
    implementation(libs.poi) {
        exclude(group = "org.apache.xmlbeans", module = "xmlbeans")
        exclude(group = "commons-codec", module = "commons-codec")
    }
    implementation(libs.poi.ooxml) {
        exclude(group = "org.apache.xmlbeans", module = "xmlbeans")
        exclude(group = "org.apache.commons", module = "commons-compress")
        exclude(group = "com.github.virtuald", module = "curvesapi")
    }
    // poi-ooxml 解析 xlsx 需要 commons-compress（ZipArchiveInputStream）
    implementation("org.apache.commons:commons-compress:1.26.0") {
        exclude(group = "org.apache.commons", module = "commons-lang3")
        exclude(group = "commons-io", module = "commons-io")
    }
    // Android 兼容的 XMLBeans 替代（poi 需要它解析 xlsx）
    implementation("org.apache.xmlbeans:xmlbeans:5.1.1") {
        exclude(group = "org.apache.logging.log4j")
    }
    // Android 上缺失的 stax-api 替代
    implementation("javax.xml.stream:stax-api:1.0-2")

    // Paddle Lite OCR —— 本地 JAR，可选依赖
    // CI 会下载并放入 app/libs/；本地如果没有该文件，则使用 stub 类编译并降级到 ML Kit
    val paddleJar = file("libs/PaddlePredictor.jar")
    if (paddleJar.exists()) {
        implementation(files(paddleJar))
    }

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
