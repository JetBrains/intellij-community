// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assumeVersion
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.openapi.components.service
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.model.MavenArchetype
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import java.nio.file.Path

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenArchetypeTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  private lateinit var mavenEmbedderWrappers: MavenEmbedderWrappers

  @BeforeEach
  fun setUp() {
    mavenEmbedderWrappers = maven.project.service<MavenEmbedderWrappersManager>().createMavenEmbedderWrappers()
  }

  @AfterEach
  fun tearDown() {
    mavenEmbedderWrappers.close()
  }

  @Test
  fun testInnerArchetypes() = runBlocking {
    maven.assumeVersion("bundled")

    val embedder = mavenEmbedderWrappers.getEmbedder(maven.dir)
    val archetypes = embedder.getInnerArchetypes(Path.of("/non-existing-path"))
    Assertions.assertEquals(0, archetypes.size) // at least, there were no errors
  }

  @Test
  fun testRemoteArchetypes() = runBlocking {
    maven.assumeVersion("bundled")

    val embedder = mavenEmbedderWrappers.getEmbedder(maven.dir)
    val archetypes = embedder.getRemoteArchetypes("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/")
    val filtered = archetypes
      .filter { archetype: MavenArchetype? ->
        "org.apache.maven.archetypes" == archetype!!.groupId &&
        "maven-archetype-archetype" == archetype.artifactId &&
        "1.0" == archetype.version
      }.toList()
    Assertions.assertEquals(1, filtered.size)
  }

  @Test
  fun testResolveAndGetArchetypeDescriptor() = runBlocking {
    maven.assumeVersion("bundled")

    val embedder = mavenEmbedderWrappers.getEmbedder(maven.dir)
    val descriptorMap = embedder.resolveAndGetArchetypeDescriptor(
      "org.apache.maven.archetypes",
      "maven-archetype-archetype",
      "1.0",
      mutableListOf(),
      "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/")
    assertNotNull(descriptorMap)
  }
}
