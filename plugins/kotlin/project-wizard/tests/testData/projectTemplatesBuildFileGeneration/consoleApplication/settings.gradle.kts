pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        maven("KOTLIN_BOOTSTRAP_REPO")
        maven("KOTLIN_IDE_PLUGIN_DEPENDENCIES_REPO")
        maven("KOTLIN_REPO")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}

rootProject.name = "consoleApplication"
