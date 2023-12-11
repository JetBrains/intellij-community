package com.intellij.mermaid.build

import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult

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

val Project.isTeamcity: Boolean
  get() = providers.environmentVariable("TEAMCITY_VERSION").isPresent

val Project.isAutomatedBuild: Boolean
  get() = findBooleanProperty("automatedProductionBuild") || System.getenv("AUTOMATED_PRODUCTION_BUILD") != null

val Project.publishChannel: PublishChannel
  get() = PublishChannel.parse(System.getenv("MARKETPLACE_CHANNEL"))

val Project.marketplaceToken: String
  get() = System.getenv("MARKETPLACE_TOKEN") ?: "NONE"

fun AbstractTestTask.afterSuite(block: (TestDescriptor, TestResult) -> Unit) {
  addTestListener(object: TestListener {
    override fun beforeSuite(suite: TestDescriptor) = Unit
    override fun beforeTest(testDescriptor: TestDescriptor) = Unit
    override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) = Unit

    override fun afterSuite(suite: TestDescriptor, result: TestResult) {
      block(suite, result)
    }
  })
}

fun TestResult.createResultMessage(): String {
  return buildString {
    append("Test result: $resultType ")
    append("($testCount tests, $successfulTestCount succeeded, $failedTestCount failed, $skippedTestCount skipped)")
  }
}
