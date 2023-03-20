pluginManagement {
    repositories {
        mavenLocal()
        google()
        gradlePluginPortal()
        maven("KOTLIN_BOOTSTRAP_REPO")
        maven("KOTLIN_REPO")
    }

}
rootProject.name = "generatedProject"


include(":android")
