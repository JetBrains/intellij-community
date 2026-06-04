import com.intellij.mermaid.build.properties
import com.intellij.mermaid.build.publishChannel
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin

plugins {
  kotlin("js") apply false
}

group = "com.intellij.mermaid"
version = obtainVersion()

fun obtainVersion(): String {
  val base = properties("pluginVersion")
  val suffix = findProperty("versionSuffix")
  val metadata = findProperty("versionMetadata")
  return buildString {
    append(base)
    if (suffix != null) {
      append("-")
      append(suffix)
    }
    if (metadata != null) {
      append("+")
      append(metadata)
    }
  }
}

subprojects {
  group = rootProject.group
  version = rootProject.version
}

afterEvaluate {
  logger.lifecycle("Publish channel is: $publishChannel")
  logger.lifecycle("Project version is: $version")
}

project.plugins.withType<NodeJsRootPlugin>().configureEach {
  extensions.configure<NodeJsRootExtension> {
    nodeVersion = "20.11.1"
    if (findProperty("useNodeMirror")?.toString()?.toBoolean() != false) {
      nodeDownloadBaseUrl = "https://packages.jetbrains.team/files/p/grazi/node-mirror"
    }
  }
}
