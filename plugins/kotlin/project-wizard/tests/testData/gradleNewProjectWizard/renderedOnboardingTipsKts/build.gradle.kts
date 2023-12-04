plugins {
    kotlin("jvm") version "KOTLIN_VERSION"
}

group = "org.testcase"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(JDK_VERSION)
}
