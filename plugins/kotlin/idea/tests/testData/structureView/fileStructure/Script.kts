plugins {
    kotlin("jvm") version "1.7.10"
    application
}

repositories {
    mavenCentral()<caret>
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
}

fun foo() {}