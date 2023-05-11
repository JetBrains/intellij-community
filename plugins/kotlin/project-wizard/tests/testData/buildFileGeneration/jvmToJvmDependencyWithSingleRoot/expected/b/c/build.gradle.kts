plugins {
    kotlin("jvm")
}

group = "testGroupId"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(project(":b"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(11)
}