pluginManagement {
    repositories {
        mavenLocal()
        google()
        jcenter()
        gradlePluginPortal()
        maven("KOTLIN_BOOTSTRAP_REPO")
        maven("KOTLIN_REPO")
    }

}
rootProject.name = "generatedProject"


include(":android")