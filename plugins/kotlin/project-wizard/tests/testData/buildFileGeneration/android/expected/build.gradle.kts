buildscript {
    repositories {
        gradlePluginPortal()
        google()
        maven("KOTLIN_BOOTSTRAP_REPO")
        maven("KOTLIN_IDE_PLUGIN_DEPENDENCIES_REPO")
        maven("KOTLIN_REPO")
        mavenLocal()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:KOTLIN_VERSION")
        classpath("com.android.tools.build:gradle:8.1.0")
    }
}

group = "testGroupId"
version = "1.0-SNAPSHOT"

allprojects {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        maven("KOTLIN_BOOTSTRAP_REPO")
        maven("KOTLIN_IDE_PLUGIN_DEPENDENCIES_REPO")
        maven("KOTLIN_REPO")
    }
}
