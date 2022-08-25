plugins {
    kotlin("multiplatform") version "KOTLIN_VERSION"
}

group = "testGroupId"
version = "1.0-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()
    maven("KOTLIN_BOOTSTRAP_REPO")
    maven("KOTLIN_REPO")
}

kotlin {
    js("nodeJs", IR) {
        binaries.executable()
        nodejs {

        }
    }
    js("browser", IR) {
        binaries.executable()
        browser {
            commonWebpackConfig {
                cssSupport {
                    enabled.set(true)
                }
            }
        }
    }
    sourceSets {
        val nodeJsMain by getting
        val nodeJsTest by getting
        val browserMain by getting
        val browserTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
