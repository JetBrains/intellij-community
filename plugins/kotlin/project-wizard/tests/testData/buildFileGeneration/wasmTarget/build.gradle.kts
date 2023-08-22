plugins {
    kotlin("multiplatform") version "KOTLIN_VERSION"
}

group = "testGroupId"
version = "1.0-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()
    maven("KOTLIN_BOOTSTRAP_REPO")
    maven("KOTLIN_IDE_PLUGIN_DEPENDENCIES_REPO")
    maven("KOTLIN_REPO")
}

kotlin {
    wasm("wasmSimple") {
        binaries.executable()
        browser {

        }
    }
    sourceSets {
        val wasmSimpleMain by getting
        val wasmSimpleTest by getting
    }
}
