// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom

import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.indices.MavenIndicesManager
import org.junit.Test
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors

class MavenPluginIndexTest : MavenDomWithIndicesTestCase() {
  @Test
  fun testDefaultPluginsDownloadedAndIndexed() = runBlocking {
    runAndExpectPluginIndexEvents(DEFAULT_PLUGIN_ARTIFACT_IDS) {
      runAndExpectArtifactDownloadEvents(DEFAULT_PLUGIN_GROUP_ID, DEFAULT_PLUGIN_ARTIFACT_IDS) {
        importProjectAsync("""
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
    val basePath = Path.of(dir.toString(), "testData", "local1").toString()
    val basePluginsPath = Path.of(basePath, *DEFAULT_PLUGIN_GROUP_ID.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
    try {

      val pluginFolders = Files.list(basePluginsPath).use { stream ->
        stream.map { it.fileName.toString() }.collect(Collectors.toSet())
      }

      val notDownloadedPlugins = HashSet(DEFAULT_PLUGIN_ARTIFACT_IDS)
      notDownloadedPlugins.removeAll(pluginFolders)
      assertTrue("Maven plugins are not downloaded: " + java.lang.String.join(", ", notDownloadedPlugins), notDownloadedPlugins.isEmpty())
    }
    catch (e: IOException) {
      throw RuntimeException(e)
    }
  }

  private fun checkIndexedPlugins() {
    val indicesManager = MavenIndicesManager.getInstance(project)
    val notIndexedPlugins = HashSet<String?>()
    for (artifactId in DEFAULT_PLUGIN_ARTIFACT_IDS) {
      val pluginIndexed = indicesManager.hasLocalArtifactId(DEFAULT_PLUGIN_GROUP_ID, artifactId)
      if (!pluginIndexed) {
        notIndexedPlugins.add(artifactId)
      }
    }
    assertTrue("Maven plugins are not indexed: " + java.lang.String.join(", ", notIndexedPlugins), notIndexedPlugins.isEmpty())
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
