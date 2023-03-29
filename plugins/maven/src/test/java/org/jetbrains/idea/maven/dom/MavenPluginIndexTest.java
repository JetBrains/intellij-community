// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom;

import org.jetbrains.idea.maven.indices.MavenIndicesManager;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class MavenPluginIndexTest extends MavenDomWithIndicesTestCase {
  private static final String DEFAULT_PLUGIN_GROUP_ID = "org.apache.maven.plugins";

  // maven 3.9 plugins
  private static final Set<String> DEFAULT_PLUGIN_ARTIFACT_IDS = Set.of(
    "maven-clean-plugin",
    "maven-compiler-plugin",
    "maven-deploy-plugin",
    "maven-install-plugin",
    "maven-jar-plugin",
    "maven-resources-plugin",
    "maven-site-plugin",
    "maven-surefire-plugin"
  );

  @Test
  public void testDefaultPluginsDownloadedAndIndexed() {
    runAndExpectPluginIndexEvents(DEFAULT_PLUGIN_ARTIFACT_IDS, () -> {
      runAndExpectArtifactDownloadEvents(DEFAULT_PLUGIN_GROUP_ID, DEFAULT_PLUGIN_ARTIFACT_IDS, () -> {
        importProject("""
                        <groupId>test</groupId>
                        <artifactId>project</artifactId>
                        <version>1</version>
                        """);

        checkDownloadedPlugins();
      });
    });

    checkIndexedPlugins();
  }

  private void checkDownloadedPlugins() {
    var basePath = Path.of(myDir.getPath(), "testData", "local1").toString();
    var basePluginsPath = Path.of(basePath, DEFAULT_PLUGIN_GROUP_ID.split("\\."));
    try {
      Set<String> pluginFolders = Files.list(basePluginsPath).map(Path::getFileName).map(Path::toString).collect(Collectors.toSet());
      var notDownloadedPlugins = new HashSet<>(DEFAULT_PLUGIN_ARTIFACT_IDS);
      notDownloadedPlugins.removeAll(pluginFolders);
      assertTrue("Maven plugins are not downloaded: " + String.join(", ", notDownloadedPlugins), notDownloadedPlugins.isEmpty());    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void checkIndexedPlugins() {
    var indicesManager = MavenIndicesManager.getInstance(myProject);
    var notIndexedPlugins = new HashSet<String>();
    for (var artifactId : DEFAULT_PLUGIN_ARTIFACT_IDS) {
      var pluginIndexed = indicesManager.hasLocalArtifactId(DEFAULT_PLUGIN_GROUP_ID, artifactId);
      if (!pluginIndexed) {
        notIndexedPlugins.add(artifactId);
      }
    }
    assertTrue("Maven plugins are not indexed: " + String.join(", ", notIndexedPlugins), notIndexedPlugins.isEmpty());
  }
}
