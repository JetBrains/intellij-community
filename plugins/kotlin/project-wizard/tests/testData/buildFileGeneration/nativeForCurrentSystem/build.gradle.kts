plugins {
    kotlin("multiplatform") version "KOTLIN_VERSION"
}

group = "testGroupId"
version = "1.0-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()
    maven("KOTLIN_BOOTSTRAP_REPO")
    maven("KOTLIN_IDE_PLUGIN_DEPENDENCIES_REPO")
    maven("KOTLIN_REPO")
}

kotlin {
    val hostOs = System.getProperty("os.name")
    val isArm64 = System.getProperty("os.arch") == "aarch64"
    val isMingwX64 = hostOs.startsWith("Windows")
    val myNativeTarget = when {
        hostOs == "Mac OS X" && isArm64 -> macosArm64("myNative")
        hostOs == "Mac OS X" && !isArm64 -> macosX64("myNative")
        hostOs == "Linux" && isArm64 -> linuxArm64("myNative")
        hostOs == "Linux" && !isArm64 -> linuxX64("myNative")
        isMingwX64 -> mingwX64("myNative")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    
    sourceSets {
        val myNativeMain by getting
        val myNativeTest by getting
    }
}