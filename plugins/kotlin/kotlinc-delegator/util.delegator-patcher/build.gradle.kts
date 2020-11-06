// This is JPS module. this `build.gradle.kts` is only for purpose of being able to run this module on CI
plugins {
    id("org.jetbrains.kotlin.jvm") version "1.4.10"
    application
}

repositories {
    jcenter()
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}

sourceSets {
    getByName("main").java.srcDir("src")
    getByName("main").resources.srcDir("resources")
}


application {
    mainClassName = "org.jetbrains.kotlin.util.delegatorpatcher.MainKt"
}
