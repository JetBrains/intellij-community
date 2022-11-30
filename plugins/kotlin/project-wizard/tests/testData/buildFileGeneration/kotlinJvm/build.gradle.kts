plugins {
    kotlin("jvm") version "KOTLIN_VERSION"
}

group = "testGroupId"
version = "1.0-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()
    maven("KOTLIN_BOOTSTRAP_REPO")
    maven("KOTLIN_REPO")
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnit()
}

kotlin {
    jvmToolchain(8)
}