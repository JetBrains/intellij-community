import com.intellij.mermaid.build.*

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
