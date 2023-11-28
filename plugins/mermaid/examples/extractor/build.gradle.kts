import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  java
  `kotlin-dsl`
  kotlin("jvm") version "1.9.21"
  id("com.gradle.plugin-publish") version "1.1.0"
}

repositories {
  mavenCentral()
}

dependencies {
  implementation("org.jetbrains:markdown:0.4.1")
}

tasks {
  withType<JavaCompile> {
    sourceCompatibility = "17"
    targetCompatibility = "17"
  }
  withType<KotlinCompile> {
    kotlinOptions {
      jvmTarget = "17"
      languageVersion = "1.7"
      apiVersion = "1.7"
    }
  }
}

gradlePlugin {
  plugins {
    create("example-extractor") {
      id = "example-extractor"
      implementationClass = "com.intellij.mermaid.test.ExampleExtractorPlugin"
    }
  }
}
