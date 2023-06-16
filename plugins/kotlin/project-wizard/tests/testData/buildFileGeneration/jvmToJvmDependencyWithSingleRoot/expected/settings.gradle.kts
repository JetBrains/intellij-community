pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        maven("KOTLIN_BOOTSTRAP_REPO")
        maven("KOTLIN_REPO")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}

include(":b:c")
include(":b")

rootProject.name = "generatedProject"
