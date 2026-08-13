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
            // ✅ 修复：统一环境变量名，兼容 CI 和本地
            val storeFilePath = System.getenv("SIGNING_STORE_FILE") 
                ?: System.getenv("STORE_FILE") 
                ?: "release.jks"
            
            // CI 中生成在 app/release.jks，本地可能用绝对路径
            val resolvedStoreFile = if (storeFilePath == "release.jks") {
                file("${project.projectDir}/release.jks")
            } else {
                file(storeFilePath)
            }

            val storePw = System.getenv("SIGNING_STORE_PASSWORD") 
                ?: System.getenv("STORE_PASSWORD")
            val keyAliasName = System.getenv("SIGNING_KEY_ALIAS") 
                ?: System.getenv("KEY_ALIAS")
            val keyPw = System.getenv("SIGNING_KEY_PASSWORD") 
                ?: System.getenv("KEY_PASSWORD")

            // 只有当所有签名信息都存在时才配置 release 签名
            if (resolvedStoreFile.exists() && storePw != null && keyAliasName != null && keyPw != null) {
                storeFile = resolvedStoreFile
                storePassword = storePw
                keyAlias = keyAliasName
                keyPassword = keyPw
            } else {
                println("⚠️ Warning: Release signing config not fully set, falling back to debug signing")
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
            // ✅ 安全获取 release signingConfig
            signingConfig = signingConfigs.findByName("release")?.takeIf { 
                it.storeFile != null && it.storeFile!!.exists() 
            } ?: signingConfigs.getByName("debug")
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

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }
}

// ✅ 修复：将 PaddlePredictor.jar 检查从配置阶段移到任务阶段，避免 CI 无文件时直接崩溃
tasks.register("checkPaddleJar") {
    doLast {
        val paddleJar = file("libs/PaddlePredictor.jar")
        if (!paddleJar.exists()) {
            throw GradleException(
                "❌ PaddlePredictor.jar not found at ${paddleJar.absolutePath}\n" +
                "Please commit the file to app/libs/ in the repository."
            )
        }
        if (paddleJar.length() < 1024) {
            throw GradleException(
                "❌ PaddlePredictor.jar is too small (${paddleJar.length()} bytes). " +
                "The file may be corrupted."
            )
        }
        println("✅ PaddlePredictor.jar verified: ${paddleJar.length()} bytes")
    }
}

// 让 preBuild 依赖此检查，仅在构建时执行而非配置阶段
afterEvaluate {
    tasks.named("preBuild").configure {
        dependsOn("checkPaddleJar")
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

    implementation("com.google.android.material:material:1.12.0")

    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.poi) {
        exclude(group = "org.apache.xmlbeans", module = "xmlbeans")
        exclude(group = "commons-codec", module = "commons-codec")
    }
    implementation(libs.poi.ooxml) {
        exclude(group = "org.apache.xmlbeans", module = "xmlbeans")
        exclude(group = "org.apache.commons", module = "commons-compress")
        exclude(group = "com.github.virtuald", module = "curvesapi")
    }
    implementation("org.apache.commons:commons-compress:1.26.0") {
        exclude(group = "org.apache.commons", module = "commons-lang3")
        exclude(group = "commons-io", module = "commons-io")
    }
    implementation("org.apache.xmlbeans:xmlbeans:5.1.1") {
        exclude(group = "org.apache.logging.log4j")
    }
    implementation("javax.xml.stream:stax-api:1.0-2")

    // Paddle Lite JAR（仅在文件存在时引入，避免 CI 配置阶段报错）
    val paddleJar = file("libs/PaddlePredictor.jar")
    if (paddleJar.exists()) {
        implementation(files(paddleJar))
    } else {
        println("⚠️ Warning: PaddlePredictor.jar not found, skipping local dependency")
    }

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
