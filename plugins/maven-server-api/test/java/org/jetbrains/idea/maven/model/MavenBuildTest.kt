// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.model

import org.assertj.core.api.AssertionsForInterfaceTypes.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MavenBuildTest {

  @Test
  fun `maven sources should present if resources added`() {
    val build = MavenBuild()
    build.resources = listOf(MavenResource("dir", false, "target", emptyList(), emptyList()))

    assertThat(build.mavenSources).hasSize(1)
    assertEquals("resources", build.mavenSources[0].lang)
    assertEquals("main", build.mavenSources[0].scope)
    assertEquals("dir", build.mavenSources[0].directory)
    assertEquals("target", build.mavenSources[0].targetPath)
  }

  @Test
  fun `maven sources should present if test resources added`() {
    val build = MavenBuild()
    build.mavenSources = listOf(
      MavenSource.fromSourceTag("resDir",
                                emptyList(),
                                emptyList(),
                                "main",
                                "resources",
                                "target",
                                "", false, true),
      MavenSource.fromSourceTag("testResDir",
                                emptyList(),
                                emptyList(),
                                "test",
                                "resources",
                                "target",
                                "", false, true))
    assertThat(build.sources).isEmpty()
    assertThat(build.testSources).isEmpty()
    assertThat(build.resources).containsExactly(MavenResource(
      "resDir", false, "target", emptyList(), emptyList()
    ))

    assertThat(build.testResources).containsExactly(MavenResource(
      "testResDir", false, "target", emptyList(), emptyList()
    ))
  }


  @Test
  fun `maven resources should present if mavensources  added`() {
    val build = MavenBuild()
    build.testResources = listOf(MavenResource("dir", false, "target", emptyList(), emptyList()))

    assertThat(build.mavenSources).hasSize(1)
    assertEquals("resources", build.mavenSources[0].lang)
    assertEquals("test", build.mavenSources[0].scope)
    assertEquals("dir", build.mavenSources[0].directory)
    assertEquals("target", build.mavenSources[0].targetPath)
  }

  @Test
  fun `maven sources should present if sources added`() {
    val build = MavenBuild()
    build.sources = listOf("src")
    assertThat(build.mavenSources).hasSize(1)
    assertEquals("java", build.mavenSources[0].lang)
    assertEquals("src", build.mavenSources[0].directory)
    assertEquals("main", build.mavenSources[0].scope)
  }

  @Test
  fun `maven sources should present if test sources added`() {
    val build = MavenBuild()
    build.testSources = listOf("testSrc")
    assertThat(build.mavenSources).hasSize(1)
    assertEquals("java", build.mavenSources[0].lang)
    assertEquals("testSrc", build.mavenSources[0].directory)
    assertEquals("test", build.mavenSources[0].scope)
  }

  @Test
  fun `sources present if maven sources added`() {
    val build = MavenBuild()
    build.mavenSources = listOf(
      MavenSource.fromSourceTag("srcDir",
                                emptyList(),
                                emptyList(),
                                "main",
                                "java",
                                "target",
                                "", false, true),
      MavenSource.fromSourceTag("testDir",
                                emptyList(),
                                emptyList(),
                                "test",
                                "java",
                                "target",
                                "", false, true))
    assertThat(build.sources).containsExactly("srcDir")
    assertThat(build.testSources).containsExactly("testDir")
  }
}