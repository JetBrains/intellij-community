// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.components.service
import com.intellij.testFramework.RunAll
import com.intellij.util.ThrowableRunnable
import junit.framework.TestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.model.MavenArchetype
import org.jetbrains.idea.maven.model.MavenRemoteRepository
import org.junit.Test
import java.nio.file.Path

class MavenArchetypeTest : MavenMultiVersionImportingTestCase() {
  private lateinit var mavenEmbedderWrappers: MavenEmbedderWrappers

  override fun setUp() {
    super.setUp()
    mavenEmbedderWrappers = project.service<MavenEmbedderWrappersManager>().createMavenEmbedderWrappers()
  }

  override fun tearDown() {
    RunAll(
      ThrowableRunnable { mavenEmbedderWrappers.close() },
      ThrowableRunnable { super.tearDown() }
    ).run()
  }

  @Test
  fun testInnerArchetypes() = runBlocking {
    assumeVersion("bundled")

    val embedder = mavenEmbedderWrappers.getEmbedder(dir.toString())
    val archetypes = embedder.getInnerArchetypes(Path.of("/non-existing-path"))
    TestCase.assertEquals(0, archetypes.size) // at least, there were no errors
  }

  @Test
  fun testRemoteArchetypes() = runBlocking {
    assumeVersion("bundled")

    val embedder = mavenEmbedderWrappers.getEmbedder(dir.toString())
    val archetypes = embedder.getRemoteArchetypes("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/")
    val filtered = archetypes
      .filter { archetype: MavenArchetype? ->
        "org.apache.maven.archetypes" == archetype!!.groupId &&
        "maven-archetype-archetype" == archetype.artifactId &&
        "1.0" == archetype.version
      }.toList()
    TestCase.assertEquals(1, filtered.size)
  }

  @Test
  fun testResolveAndGetArchetypeDescriptor() = runBlocking {
    assumeVersion("bundled")

    val embedder = mavenEmbedderWrappers.getEmbedder(dir.toString())
    val descriptorMap = embedder.resolveAndGetArchetypeDescriptor(
      "org.apache.maven.archetypes",
      "maven-archetype-archetype",
      "1.0",
      mutableListOf<MavenRemoteRepository>(),
      "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/")
    assertNotNull(descriptorMap)
  }
}
