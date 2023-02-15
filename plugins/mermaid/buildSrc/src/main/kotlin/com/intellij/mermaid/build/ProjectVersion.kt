package com.intellij.mermaid.build

import org.gradle.api.Project
import org.gradle.util.internal.VersionNumber
import org.jetbrains.intellij.pluginRepository.PluginRepositoryFactory
import org.jetbrains.intellij.pluginRepository.model.UpdateBean

private fun Project.obtainLatestCompatibleUpdates(channel: PublishChannel): Sequence<UpdateBean> {
  val repository = PluginRepositoryFactory.create("https://plugins.jetbrains.com", marketplaceToken)
  val updates = repository.pluginManager.searchCompatibleUpdates(
    xmlIds = listOf("com.intellij.mermaid"),
    channel = channel.actualName
  )
  return updates.asSequence()
}

/**
 * Obtains latest plugin version for [channel] from marketplace.
 */
fun Project.obtainLatestReleasedVersion(channel: PublishChannel): VersionNumber {
  val updates = obtainLatestCompatibleUpdates(channel)
  val versionNumbers = updates.map { VersionNumber.parse(it.version) }
  val latestVersion = versionNumbers.maxOrNull()
  checkNotNull(latestVersion) { "Failed to obtain latest version for channel $channel" }
  return latestVersion
}

private const val nightlyQualifier = "nightly"

fun VersionNumber.withNextMicro(): VersionNumber {
  return VersionNumber(major, minor, micro + 1, qualifier)
}

fun VersionNumber.withQualifier(qualifier: String): VersionNumber {
  return VersionNumber(major, minor, micro, qualifier)
}

fun VersionNumber.withNightlyQualifier(iteration: Int): VersionNumber {
  return withQualifier("${nightlyQualifier}.$iteration")
}

fun VersionNumber.incrementNightlyVersion(): VersionNumber {
  val qualifier = qualifier ?: "$nightlyQualifier.0"
  val iterationPart = qualifier.removePrefix("$nightlyQualifier.")
  val iteration = iterationPart.toIntOrNull() ?: 0
  return withNightlyQualifier(iteration + 1)
}
