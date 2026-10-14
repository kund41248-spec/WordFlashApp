import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.wordflash.app"
    compileSdk = 35

    // ---------------------------------------------------------------
    // 发布签名配置
    //
    // 口令等敏感信息放在项目根目录的 keystore.properties 中，
    // 该文件已在 .gitignore 里排除，不会进版本库。
    //
    // 文件不存在时**不报错**，这样没有签名文件的协作者
    // 依然可以正常构建 debug 版本。
    //
    // 注意：这里必须用顶部 import 的 Properties，
    // 不能写 java.util.Properties —— 在 android {} 块内
    // `java` 指的是 Android 插件的 java 扩展对象，不是 Java 包。
    // ---------------------------------------------------------------
    val keystorePropsFile = rootProject.file("keystore.properties")
    val keystoreProps = Properties().apply {
        if (keystorePropsFile.exists()) {
            keystorePropsFile.inputStream().use { load(it) }
        }
    }
    val hasReleaseKeystore = keystorePropsFile.exists() &&
        keystoreProps.getProperty("storeFile") != null

    defaultConfig {
        applicationId = "com.wordflash.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // Room 导出 schema，便于追踪数据库结构变更
        ksp { arg("room.schemaLocation", "$projectDir/schemas") }
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                // 开启 v1 + v2 签名：v1 兼容 Android 6 及以下
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
            // debug 版加后缀，可与正式版共存于同一台手机
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 有正式签名就用正式的，否则退回调试签名（仅供本地验证）
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Room/DataStore 用到 java.time，低版本需脱糖
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // ---------- 基础 ----------
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // ---------- Compose ----------
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    // ---------- 导航 ----------
    implementation(libs.androidx.navigation.compose)

    // ---------- Room ----------
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // ---------- Hilt ----------
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // ---------- DataStore ----------
    implementation(libs.androidx.datastore.preferences)

    // ---------- 协程 ----------
    implementation(libs.kotlinx.coroutines.android)

    // ---------- 脱糖（java.time 支持） ----------
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")

    // ---------- 测试 ----------
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
