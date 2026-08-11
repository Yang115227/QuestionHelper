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
            storeFile = file("release.jks")
            storePassword = System.getenv("STORE_PASSWORD") ?: "questionhelper"
            keyAlias = System.getenv("KEY_ALIAS") ?: "questionhelper"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "questionhelper"
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
            signingConfig = signingConfigs.getByName("release")
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

    // POI (Excel 解析) —— 排除 Android 不兼容的依赖
    implementation(libs.poi) {
        exclude(group = "org.apache.xmlbeans", module = "xmlbeans")
        exclude(group = "commons-codec", module = "commons-codec")
    }
    implementation(libs.poi.ooxml) {
        exclude(group = "org.apache.xmlbeans", module = "xmlbeans")
        exclude(group = "org.apache.commons", module = "commons-compress")
        exclude(group = "com.github.virtuald", module = "curvesapi")
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
