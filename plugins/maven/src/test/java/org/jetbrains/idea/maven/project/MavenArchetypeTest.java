// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;

public class MavenArchetypeTest extends MavenMultiVersionImportingTestCase {
  private MavenEmbeddersManager myManager;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myManager = new MavenEmbeddersManager(getProject());
  }

  @Override
  protected void tearDownFixtures() throws Exception {
    super.tearDownFixtures();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myManager.releaseForcefullyInTests();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  @Test
  public void testInnerArchetypes() {
    assumeVersion("bundled");

    var embedder = myManager.getEmbedder(MavenEmbeddersManager.FOR_FOLDERS_RESOLVE, getDir().getPath());
    var archetypes = embedder.getInnerArchetypes(Path.of("/non-existing-path"));
    assertEquals(0, archetypes.size()); // at least, there were no errors
  }

  @Test
  public void testRemoteArchetypes() {
    assumeVersion("bundled");

    var embedder = myManager.getEmbedder(MavenEmbeddersManager.FOR_FOLDERS_RESOLVE, getDir().getPath());
    var archetypes = embedder.getRemoteArchetypes("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/");
    var filtered = archetypes.stream()
      .filter(archetype ->
                "org.apache.maven.archetypes".equals(archetype.groupId) &&
                "maven-archetype-archetype".equals(archetype.artifactId) &&
                "1.0".equals(archetype.version)
      ).toList();
    assertEquals(1, filtered.size());
  }

  @Test
  public void testResolveAndGetArchetypeDescriptor() {
    assumeVersion("bundled");

    var embedder = myManager.getEmbedder(MavenEmbeddersManager.FOR_FOLDERS_RESOLVE, getDir().getPath());
    var descriptorMap = embedder.resolveAndGetArchetypeDescriptor(
      "org.apache.maven.archetypes",
      "maven-archetype-archetype",
      "1.0",
      List.of(),
      "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/");
    assertNotNull(descriptorMap);
  }
}
