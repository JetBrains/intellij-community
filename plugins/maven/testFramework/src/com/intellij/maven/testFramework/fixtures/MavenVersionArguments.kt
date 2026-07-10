  // Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework.fixtures

import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.server.MavenDistributionsCache
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.support.ParameterDeclarations
import java.util.stream.Stream

/**
 * Maven (and Maven model) versions the JUnit 5 Maven DOM tests run against, mirroring the legacy
 * `MavenMultiVersionImportingTestCase`. Honors the `maven.versions.to.run` system property (comma-separated
 * `version[/model]` entries) for CI; defaults to [MAVEN_VERSIONS] otherwise.
 */
object MavenTestVersions {
  const val MAVEN_4_VERSION: String = "4.0.0-rc-5"

  val MAVEN_VERSIONS: Array<String> = arrayOf(
    "bundled",
    "4/4.0.0",
  )

  /** Returns the (mavenVersion, mavenModelVersion) pairs to run, parsed from `maven.versions.to.run` or [MAVEN_VERSIONS]. */
  fun versionsToRun(): List<Pair<String, String>> {
    val property = System.getProperty("maven.versions.to.run")
    val entries = if (!property.isNullOrEmpty()) {
      property.split(",").filter { it.isNotEmpty() }
    }
    else {
      MAVEN_VERSIONS.toList()
    }
    return entries.map { entry ->
      val versionAndModel = entry.split('/')
      val version = versionAndModel[0]
      val model = versionAndModel.getOrElse(1) { MavenConstants.MODEL_VERSION_4_0_0 }
      check(model == MavenConstants.MODEL_VERSION_4_0_0 || model == MavenConstants.MODEL_VERSION_4_1_0) {
        "Unknown model: $model from $entry"
      }
      version to model
    }
  }

  /** Resolves a [MAVEN_VERSIONS] version token (e.g. `bundled`, `4`, `4/4.0.0`) to a concrete Maven version string. */
  fun getActualVersion(version: String): String = when {
    version == "bundled" -> MavenDistributionsCache.resolveEmbeddedMavenHome().version!!
    version == "4" -> MAVEN_4_VERSION
    version.contains('/') -> version.substringBefore('/')
    else -> version
  }
}

/**
 * JUnit 5 [ArgumentsProvider] supplying the `(mavenVersion, mavenModelVersion)` pairs from [MavenTestVersions].
 *
 * Use it on a `@ParameterizedClass` whose constructor takes `(mavenVersion: String, modelVersion: String)`:
 * ```
 * @TestApplication
 * @ParameterizedClass
 * @ArgumentsSource(MavenVersionArguments::class)
 * class MyTest(mavenVersion: String, modelVersion: String) {
 *   private val maven by mavenDomFixture(mavenVersion = mavenVersion, modelVersion = modelVersion)
 * }
 * ```
 */
class MavenVersionArguments : ArgumentsProvider {
  override fun provideArguments(parameters: ParameterDeclarations?, context: ExtensionContext?): Stream<out Arguments?> {
    return MavenTestVersions.versionsToRun().map { Arguments.of(it.first, it.second) }.stream()
  }
}
