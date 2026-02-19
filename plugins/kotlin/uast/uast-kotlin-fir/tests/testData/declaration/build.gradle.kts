// KT-61431

plugins {
    id("com.android.application") version "2.3.3"
    // Deprecated version of the above (shouldn't be used in real KTS file,
    // but here to check that visitors also touch method calls
    id("android") version "2.3.3"
    kotlin("android") version "1.1.51"
}

android {
    compileSdkVersion(23)

    defaultConfig {
        minSdkVersion(7)
        targetSdkVersion(23)

        applicationId = "com.example.kotlingradle"
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles("proguard-rules.pro")
        }
    }
}

dependencies {
    compile("com.android.support:appcompat-v7:23.4.0")
    compile("com.android.support.constraint:constraint-layout:1.0.0-alpha8")
    compile(kotlin("stdlib", "1.1.51"))
}

repositories {
    jcenter()
}
