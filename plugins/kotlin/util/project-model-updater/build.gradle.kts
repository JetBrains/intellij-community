plugins {
	kotlin("jvm") version "2.2.20"
    id("application")
}

repositories {
    maven("https://cache-redirector.jetbrains.com/maven-central")
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.intellij.deps:jdom:2.0.6")
}

kotlin {
    jvmToolchain(17)
}

sourceSets.main.configure {
    java.srcDirs("src")
    resources.srcDir("resources")
}

application {
    mainClass.set("org.jetbrains.tools.model.updater.MainKt")
}
