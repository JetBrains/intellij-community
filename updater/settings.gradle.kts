pluginManagement {
  repositories {
    maven("https://cache-redirector.jetbrains.com/plugins.gradle.org")
  }
}

plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "updater"
