pluginManagement {
    repositories {
        mavenLocal()
        google()
        gradlePluginPortal()
        maven("KOTLIN_BOOTSTRAP_REPO")
        maven("KOTLIN_REPO")
    }
}

include(":android")

rootProject.name = "generatedProject"
