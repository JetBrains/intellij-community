// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom

import com.intellij.maven.testFramework.fixtures.MavenDomTestFixtureIndices
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.fixtures.runAndExpectArtifactDownloadEvents
import org.jetbrains.idea.maven.fixtures.runAndExpectPluginIndexEvents
import org.jetbrains.idea.maven.indices.MavenIndicesManager
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenPluginIndexTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion, modelVersion = modelVersion,
    skipPluginResolution = false,
    indices = MavenDomTestFixtureIndices("local1", listOf("local2")),
  )

  @Test
  fun testDefaultPluginsDownloadedAndIndexed() = runBlocking {
    maven.runAndExpectPluginIndexEvents(DEFAULT_PLUGIN_ARTIFACT_IDS) {
      maven.runAndExpectArtifactDownloadEvents(DEFAULT_PLUGIN_GROUP_ID, DEFAULT_PLUGIN_ARTIFACT_IDS) {
        maven.importProjectAsync("""
                        <groupId>test</groupId>
                        <artifactId>project</artifactId>
                        <version>1</version>
                        """.trimIndent())
        checkDownloadedPlugins()
      }
    }

    checkIndexedPlugins()
  }

  private fun checkDownloadedPlugins() {
    val basePath = Path.of(maven.dir.toString(), "testData", "local1").toString()
    val basePluginsPath = Path.of(basePath, *DEFAULT_PLUGIN_GROUP_ID.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
    try {

      val pluginFolders = Files.list(basePluginsPath).use { stream ->
        stream.map { it.fileName.toString() }.collect(Collectors.toSet())
      }

      val notDownloadedPlugins = HashSet(DEFAULT_PLUGIN_ARTIFACT_IDS)
      notDownloadedPlugins.removeAll(pluginFolders)
      assertTrue(notDownloadedPlugins.isEmpty(), "Maven plugins are not downloaded: " + java.lang.String.join(", ", notDownloadedPlugins))
    }
    catch (e: IOException) {
      throw RuntimeException(e)
    }
  }

  private fun checkIndexedPlugins() {
    val indicesManager = MavenIndicesManager.getInstance(maven.project)
    val notIndexedPlugins = HashSet<String?>()
    for (artifactId in DEFAULT_PLUGIN_ARTIFACT_IDS) {
      val pluginIndexed = indicesManager.hasLocalArtifactId(DEFAULT_PLUGIN_GROUP_ID, artifactId)
      if (!pluginIndexed) {
        notIndexedPlugins.add(artifactId)
      }
    }
    assertTrue(notIndexedPlugins.isEmpty(), "Maven plugins are not indexed: " + java.lang.String.join(", ", notIndexedPlugins))
  }

  companion object {
    private const val DEFAULT_PLUGIN_GROUP_ID = "org.apache.maven.plugins"

    // maven 3.9 plugins
    private val DEFAULT_PLUGIN_ARTIFACT_IDS: Set<String> = setOf(
      "maven-clean-plugin",
      "maven-compiler-plugin",
      "maven-deploy-plugin",
      "maven-install-plugin",
      "maven-jar-plugin",
      "maven-resources-plugin",
      "maven-site-plugin",
      "maven-surefire-plugin"
    )
  }
}
