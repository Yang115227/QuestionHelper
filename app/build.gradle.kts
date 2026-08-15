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

        // 如果只保留 arm64-v8a，可以保留；也可删除，让 Gradle 自动包含所有 ABI
        // ML Kit 支持所有 ABI，但如果你只针对 arm64，可保留该过滤
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        create("release") {
            val storeFilePath = System.getenv("SIGNING_STORE_FILE")
                ?: System.getenv("STORE_FILE")
                ?: ""

            val storePw = System.getenv("SIGNING_STORE_PASSWORD")
                ?: System.getenv("STORE_PASSWORD")
            val keyAliasName = System.getenv("SIGNING_KEY_ALIAS")
                ?: System.getenv("KEY_ALIAS")
            val keyPw = System.getenv("SIGNING_KEY_PASSWORD")
                ?: System.getenv("KEY_PASSWORD")

            if (storeFilePath.isNotBlank() && storePw != null && keyAliasName != null && keyPw != null) {
                val resolvedFile = if (storeFilePath == "release.jks") {
                    file("${project.projectDir}/release.jks")
                } else {
                    file(storeFilePath)
                }
                if (resolvedFile.exists()) {
                    storeFile = resolvedFile
                    storePassword = storePw
                    keyAlias = keyAliasName
                    keyPassword = keyPw
                } else {
                    println("⚠️ Warning: Store file not found at ${resolvedFile.absolutePath}, falling back to debug signing")
                }
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

    // 只保留 tflite/lite 等可能被其他库用到的后缀，移除 nb/txt
    androidResources {
        noCompress += listOf("tflite", "lite")
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

    // ===== ML Kit 中文识别（捆绑式离线模型）=====
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    // 协程与 Google Task 的桥接
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    // =============================================

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}