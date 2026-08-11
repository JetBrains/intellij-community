// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
plugins {
	kotlin("jvm") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
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
    implementation("com.charleskorn.kaml:kaml:0.99.0")
}

kotlin {
    jvmToolchain(21)
}

sourceSets.main.configure {
    java.srcDirs("src")
    resources.srcDir("resources")
}

application {
    mainClass.set("org.jetbrains.tools.model.updater.MainKt")
}
