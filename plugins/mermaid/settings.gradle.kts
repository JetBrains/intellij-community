rootProject.name = "mermaid"

include(":plugin")
include(":browser:mermaid-api")
include(":browser:extension")

dependencyResolutionManagement {
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
    val kotlinVersion = "1.7.20"
    kotlin("jvm") version kotlinVersion
    kotlin("js") version kotlinVersion
    id("com.github.ben-manes.versions") version "0.41.0"
  }
}
