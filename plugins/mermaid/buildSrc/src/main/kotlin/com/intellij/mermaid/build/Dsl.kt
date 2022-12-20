package com.intellij.mermaid.build

import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyHandler

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
