rootProject.name = "mermaid"

include(":plugin")
include(":browser:mermaid-api")
include(":browser:extension")
include(":examples:test-data")
includeBuild("examples/extractor")

dependencyResolutionManagement {
  @Suppress("UnstableApiUsage")
  repositories {
    mavenCentral()
    maven("https://www.jetbrains.com/intellij-repository/snapshots")
  }
}

pluginManagement {
  repositories {
    gradlePluginPortal()
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
  }
  plugins {
    val kotlinVersion = "1.9.21"
    kotlin("jvm") version kotlinVersion apply false
    kotlin("js") version kotlinVersion apply false
    id("com.github.ben-manes.versions") version "0.41.0"
  }
}
