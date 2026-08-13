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
                println("Warning: Release signing config not fully set, falling back to debug signing")
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

    // ✅ 修复：强制排除 stub，避免与 JAR 类冲突
    sourceSets["main"].java {
        val paddleJar = file("libs/PaddlePredictor.jar")
        val stubDir = file("src/stub/java")
        if (!paddleJar.exists()) {
            srcDir(stubDir)
            println("⚠️ PaddlePredictor.jar not found, using stub classes")
        } else {
            println("✅ PaddlePredictor.jar found, excluding stub classes")
            // 显式排除 stub 目录，防止增量构建缓存导致冲突
            setSrcDirs(srcDirs.filter { !it.absolutePath.contains("src/stub") })
        }
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

    // Paddle Lite OCR
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
