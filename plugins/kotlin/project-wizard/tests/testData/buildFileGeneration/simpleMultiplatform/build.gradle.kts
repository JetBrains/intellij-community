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
    jvm {
        jvmToolchain(11)
        withJava()
        testRuns.named("test") {
            executionTask.configure {
                useJUnit()
            }
        }
    }
    js("a") {
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
        val jvmMain by getting
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val aMain by getting
        val aTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
