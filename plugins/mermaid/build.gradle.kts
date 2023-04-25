import com.intellij.mermaid.build.*
import org.gradle.util.internal.VersionNumber

group = properties("pluginGroup")
version = properties("pluginVersion")

subprojects {
  group = rootProject.group
  version = rootProject.version
}

afterEvaluate {
  logger.lifecycle("Publish channel is: $publishChannel")
  logger.lifecycle("Project version is: $version")
}
