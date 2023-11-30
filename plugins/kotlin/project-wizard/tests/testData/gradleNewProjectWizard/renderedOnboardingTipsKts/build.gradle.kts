plugins {
    kotlin("jvm") version "KOTLIN_VERSION"
}

group = "org.testcase"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(JDK_VERSION)
}
