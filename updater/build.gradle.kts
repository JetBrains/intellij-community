plugins {
  java
}

repositories {
  mavenCentral()
}

sourceSets.main {
  java.setSrcDirs(listOf("src"))
  resources.setSrcDirs(listOf("resources"))
}
sourceSets.test {
  java.setSrcDirs(listOf("testSrc"))
}

dependencies {
  implementation("org.jetbrains:annotations:24.0.0")
  implementation("net.java.dev.jna:jna-platform:5.13.0")
  testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
  testImplementation("org.assertj:assertj-core:3.24.2")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
  sourceCompatibility = JavaVersion.VERSION_11
}

tasks.test {
  useJUnitPlatform()
}

tasks.register<Jar>("fatJar") {
  archiveFileName = "updater-full.jar"
  group = "build"
  manifest.attributes["Main-Class"] = "com.intellij.updater.Bootstrap"
  manifest.attributes["Patcher-Version"] = "3.0"
  from(sourceSets.main.get().output)
  dependsOn(configurations.runtimeClasspath)
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
