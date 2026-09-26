// Copyright 2000-2016 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven

import com.intellij.maven.testFramework.fixtures.MavenCustomRepositoryHelper
import com.intellij.maven.testFramework.fixtures.assertOrderedElementsAreEqual
import com.intellij.maven.testFramework.fixtures.mavenFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.maven.utils.MavenArtifactUtil
import org.jetbrains.idea.maven.utils.MavenPluginInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path

@TestApplication
class MavenPluginInfoReaderTest {
  private val maven by mavenFixture()

  private var p: MavenPluginInfo? = null

  @BeforeEach
  fun setUp() {
    val repositoryPath = MavenCustomRepositoryHelper(maven.dir, "plugins").getTestData("plugins")
    p = MavenArtifactUtil.readPluginInfo(Path.of(repositoryPath.toAbsolutePath().toString(), "org/apache/maven/plugins", "maven-compiler-plugin", "2.0.2", "maven-compiler-plugin-2.0.2.jar"))
  }

  @Test
  fun testLoadingPluginInfo() {
    assertEquals("org.apache.maven.plugins", p!!.groupId)
    assertEquals("maven-compiler-plugin", p!!.artifactId)
    assertEquals("2.0.2", p!!.version)
  }

  @Test
  fun testGoals() {
    assertEquals("compiler", p!!.goalPrefix)

    val qualifiedGoals: MutableList<String> = ArrayList()
    val displayNames: MutableList<String> = ArrayList()
    val goals: MutableList<String> = ArrayList()
    for (m in p!!.mojos) {
      goals.add(m.goal)
      qualifiedGoals.add(m.qualifiedGoal)
      displayNames.add(m.displayName)
    }

    assertOrderedElementsAreEqual(goals, "compile", "testCompile")
    assertOrderedElementsAreEqual(qualifiedGoals,
                                  "org.apache.maven.plugins:maven-compiler-plugin:2.0.2:compile",
                                  "org.apache.maven.plugins:maven-compiler-plugin:2.0.2:testCompile")
    assertOrderedElementsAreEqual(displayNames, "compiler:compile", "compiler:testCompile")
  }
}
