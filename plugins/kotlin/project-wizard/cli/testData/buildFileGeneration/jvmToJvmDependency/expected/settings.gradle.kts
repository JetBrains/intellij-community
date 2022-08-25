pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        maven("KOTLIN_BOOTSTRAP_REPO")
        maven("KOTLIN_REPO")
    }

}
rootProject.name = "generatedProject"


include(":a")
include(":b")
include(":c")
include(":d")