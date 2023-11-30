pluginManagement {
    repositories {
        mavenLocal()
        google()
        gradlePluginPortal()
        maven("KOTLIN_BOOTSTRAP_REPO")
        maven("KOTLIN_IDE_PLUGIN_DEPENDENCIES_REPO")
        maven("KOTLIN_REPO")
    }
}

include(":android")

rootProject.name = "generatedProject"
