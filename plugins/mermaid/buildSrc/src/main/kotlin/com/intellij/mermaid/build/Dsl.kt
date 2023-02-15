package com.intellij.mermaid.build

import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.util.internal.VersionNumber

fun DependencyHandler.project(path: String, configuration: Configuration): Dependency {
  return project(mapOf(
    "path" to path,
    "configuration" to configuration.name
  ))
}

fun DependencyHandler.project(path: String, configuration: NamedDomainObjectProvider<Configuration>): Dependency {
  return project(mapOf(
    "path" to path,
    "configuration" to configuration.name
  ))
}

fun Project.properties(key: String): String {
  return findProperty(key).toString()
}

fun Project.findBooleanProperty(name: String): Boolean {
  val property = findProperty(name) as? String
  return property.toBoolean()
}

val Project.isAutomatedBuild: Boolean
  get() = findBooleanProperty("automatedProductionBuild") || System.getenv("AUTOMATED_PRODUCTION_BUILD") != null

val Project.mainVersion: VersionNumber
  get() = VersionNumber.parse(properties("pluginVersion"))

val Project.publishChannel: PublishChannel
  get() = PublishChannel.parse(System.getenv("MARKETPLACE_CHANNEL"))

val Project.marketplaceToken: String
  get() = System.getenv("MARKETPLACE_TOKEN") ?: "NONE"
