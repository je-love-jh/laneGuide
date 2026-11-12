plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.laneguide.app"
    compileSdk = 34

    // 안드로이드 자동차(AAOS) 표준 API: 주행 중 제한(CarUxRestrictions) 등에 필요
    useLibrary("android.car")

    defaultConfig {
        applicationId = "com.laneguide.app"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Pleos Vehicle SDK: 크루즈 상태, 방향지시등 신호
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    // implementation("ai.pleos.playground:NaviHelper:3.0.5")
    // implementation("ai.pleos.playground:TextToSpeech:3.3.1")
}
