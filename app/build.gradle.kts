plugins {
    id("com.android.application")
}

android {
    namespace = "com.sbnkj.assistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sbnkj.assistant"
        minSdk = 26
        targetSdk = 35
        versionCode = 11
        versionName = "2.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 1. 新增：签名配置 (必须放在 buildTypes 之前)
    signingConfigs {
        // debug 通常系统默认存在，所以用 getByName 修改
        getByName("debug") {
            storeFile = file("sbnkj.jks")
            storePassword = "123456"
            keyAlias = "key"
            keyPassword = "123456"
        }
        // release 默认不存在，所以用 create 创建
        create("release") {
            storeFile = file("sbnkj.jks")
            storePassword = "123456"
            keyAlias = "key"
            keyPassword = "123456"
        }
    }

    // 2. 修改：构建类型
    buildTypes {
        // 新增 debug 闭包并绑定签名
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }

        release {
            // 绑定 release 签名
            signingConfig = signingConfigs.getByName("release")
            // 开启混淆和资源压缩 (你提供的配置要求是 true)
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation ("com.amitshekhar.android:debug-db:1.0.6")

    implementation("net.zetetic:sqlcipher-android:4.14.1@aar")
    implementation("androidx.sqlite:sqlite:2.6.2")
    compileOnly(files("libs\\oplus_mdm_sdk-secure.jar"))
}