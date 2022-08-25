buildscript {
    repositories {
        gradlePluginPortal()
        jcenter()
        google()
        maven("KOTLIN_BOOTSTRAP_REPO")
        maven("KOTLIN_REPO")
        mavenLocal()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:KOTLIN_VERSION")
        classpath("com.android.tools.build:gradle:7.0.4")
    }
}

group = "testGroupId"
version = "1.0-SNAPSHOT"

allprojects {
    repositories {
        mavenLocal()
        google()
        jcenter()
        mavenCentral()
        maven("KOTLIN_BOOTSTRAP_REPO")
        maven("KOTLIN_REPO")
    }
}