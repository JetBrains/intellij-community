// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
plugins {
  java
}

repositories {
  maven("https://cache-redirector.jetbrains.com/maven-central")
}

sourceSets.main {
  java.setSrcDirs(listOf("src"))
  resources.setSrcDirs(listOf("resources"))
}
sourceSets.test {
  java.setSrcDirs(listOf("testSrc"))
}

dependencies {
  implementation("org.jetbrains:annotations:26.1.0")
  implementation("net.java.dev.jna:jna-platform:5.19.1")
  testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
  testImplementation("org.assertj:assertj-core:3.24.2")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
  // 25: 262, 21: 253, 17: 223, ...
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

tasks.test {
  useJUnitPlatform()
}

tasks.register<Jar>("fatJar") {
  description = "Building the self-containing .jar file"
  group = "build"
  dependsOn(configurations.runtimeClasspath)

  archiveFileName = "updater-full.jar"
  manifest.attributes["Main-Class"] = "com.intellij.updater.Bootstrap"
  manifest.attributes["Patcher-Version"] = "3.0"
  from(sourceSets.main.get().output)
  from(configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map(::zipTree))
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
  exclude(
    "module-info.class",
    "META-INF/",
    "com/sun/jna/aix-*/",
    "com/sun/jna/freebsd-*/",
    "com/sun/jna/openbsd-*/",
    "com/sun/jna/sunos-*/",
    "com/sun/jna/*-arm*/",
    "com/sun/jna/*-loong*/",
    "com/sun/jna/*-mips*/",
    "com/sun/jna/*-ppc*/",
    "com/sun/jna/*-s390*/",
    "com/sun/jna/*-x86/"
  )
}
