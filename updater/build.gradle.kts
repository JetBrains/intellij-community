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

val java22 by sourceSets.creating {
  java.setSrcDirs(listOf("srcJava22"))
  compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
}

val java22Test by sourceSets.creating {
  java.setSrcDirs(listOf("testSrcJava22"))
  compileClasspath += java22.output + sourceSets.test.get().compileClasspath
  runtimeClasspath += java22.output + sourceSets.test.get().runtimeClasspath
}

dependencies {
  implementation("org.jetbrains:annotations:24.0.0")
  testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
  testImplementation("org.assertj:assertj-core:3.24.2")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11
}

val java25Compiler = javaToolchains.compilerFor {
  languageVersion.set(JavaLanguageVersion.of(25))
  vendor.set(JvmVendorSpec.JETBRAINS)
}
val java25Launcher = javaToolchains.launcherFor {
  languageVersion.set(JavaLanguageVersion.of(25))
  vendor.set(JvmVendorSpec.JETBRAINS)
}

tasks.withType<JavaCompile>().configureEach {
  if (name == java22.compileJavaTaskName || name == java22Test.compileJavaTaskName) {
    javaCompiler.set(java25Compiler)
    options.release.set(22)
  }
}

val fatJar = tasks.register<Jar>("fatJar") {
  archiveFileName = "updater-full.jar"
  group = "build"
  manifest.attributes(
    mapOf(
      "Main-Class" to "com.intellij.updater.Bootstrap",
      "Patcher-Version" to "3.0",
      "Multi-Release" to "true",
    )
  )
  from(sourceSets.main.get().output)
  from(java22.output) {
    into("META-INF/versions/22")
  }
  dependsOn(configurations.runtimeClasspath)
  from(configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map(::zipTree)) {
    exclude(
      "module-info.class",
      "META-INF/",
    )
  }
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.test {
  useJUnitPlatform()
  dependsOn(fatJar)
  dependsOn("java22Test")
  doFirst {
    systemProperty("updater.fat.jar", fatJar.get().archiveFile.get().asFile.absolutePath)
  }
}

tasks.register<Test>("java22Test") {
  description = "Runs tests against the Java 22 multi-release updater classes."
  group = "verification"
  dependsOn(java22Test.classesTaskName)
  testClassesDirs = java22Test.output.classesDirs
  classpath = files(java22.output, sourceSets.main.get().output, java22Test.runtimeClasspath)
  javaLauncher.set(java25Launcher)
  useJUnitPlatform()
}

tasks.check {
  dependsOn("java22Test")
}
