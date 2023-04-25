import com.intellij.mermaid.build.*

group = "com.intellij.mermaid"
version = obtainVersion()

fun obtainVersion(): String {
  val base = properties("pluginVersion")
  val suffix = findProperty("versionSuffix") ?: return base
  return "$base-$suffix"
}

subprojects {
  group = rootProject.group
  version = rootProject.version
}

afterEvaluate {
  logger.lifecycle("Publish channel is: $publishChannel")
  logger.lifecycle("Project version is: $version")
}
