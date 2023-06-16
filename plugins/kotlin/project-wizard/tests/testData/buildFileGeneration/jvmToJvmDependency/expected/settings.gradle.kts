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

include(":a")
include(":b")
include(":c")
include(":d")

rootProject.name = "generatedProject"
