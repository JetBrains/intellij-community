plugins {
    kotlin("multiplatform") version "KOTLIN_VERSION"
}

group = "testGroupId"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "9"
        }
        withJava()
        testRuns["test"].executionTask.configure {
            useJUnit()
        }
    }
    js("a", LEGACY) {
        binaries.executable()
        browser {
            commonWebpackConfig {
                cssSupport.enabled = true
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
