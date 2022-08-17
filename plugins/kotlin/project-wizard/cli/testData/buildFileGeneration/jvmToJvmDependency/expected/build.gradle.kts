group = "testGroupId"
version = "1.0-SNAPSHOT"

allprojects {
    repositories {
        mavenCentral()
        maven("KOTLIN_BOOTSTRAP_REPO")
        maven("KOTLIN_REPO")
    }
}