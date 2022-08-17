plugins {
    kotlin("js") version "KOTLIN_VERSION"
}

group = "testGroupId"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("KOTLIN_BOOTSTRAP_REPO")
    maven("KOTLIN_REPO")
}

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    js(LEGACY) {
        binaries.executable()
        nodejs {

        }
    }
}
