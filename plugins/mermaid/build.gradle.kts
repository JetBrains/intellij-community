import com.intellij.mermaid.build.*
import org.gradle.util.internal.VersionNumber

group = properties("pluginGroup")
version = obtainVersion()

subprojects {
  group = rootProject.group
  version = rootProject.version
}

afterEvaluate {
  logger.lifecycle("Publish channel is: $publishChannel")
  logger.lifecycle("Project version is: $version")
}

fun obtainNextNightlyVersion(): VersionNumber {
  val latestNightlyVersion = obtainLatestReleasedVersion(channel = PublishChannel.NIGHTLY)
  logger.lifecycle("Latest nightly version: $latestNightlyVersion")
  val latestNightlyBaseVersion = latestNightlyVersion.baseVersion
  val mainBaseVersion = mainVersion.baseVersion
  if (latestNightlyBaseVersion <= mainBaseVersion) {
    return mainVersion.withNextMicro().incrementNightlyVersion()
  }
  val next = latestNightlyVersion.incrementNightlyVersion()
  check(next > latestNightlyVersion) { "Next nightly version ($next) should be greater than latest one ($latestNightlyVersion)" }
  check(next > mainVersion) { "Next nightly version ($next) should be greater than main version ($mainVersion)" }
  return next
}

fun obtainAutomatedVersion(): VersionNumber {
  return when (publishChannel) {
    PublishChannel.NIGHTLY -> obtainNextNightlyVersion()
    PublishChannel.STABLE -> mainVersion
  }
}

fun obtainDevelopmentVersion(): VersionNumber {
  return mainVersion.withQualifier("dev")
}

fun obtainVersion(): String {
  val version = when {
    project.isAutomatedBuild -> obtainAutomatedVersion()
    else -> obtainDevelopmentVersion()
  }
  return version.toString()
}
