group = "testGroupId"
version = "1.0-SNAPSHOT"

allprojects {
    repositories {
        mavenLocal()
        mavenCentral()
        maven("KOTLIN_BOOTSTRAP_REPO")
        maven("KOTLIN_IDE_PLUGIN_DEPENDENCIES_REPO")
        maven("KOTLIN_REPO")
    }
}