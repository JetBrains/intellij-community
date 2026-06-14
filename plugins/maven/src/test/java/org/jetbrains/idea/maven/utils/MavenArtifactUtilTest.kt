// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils

import com.intellij.maven.testFramework.fixtures.mavenFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class MavenArtifactUtilTest {
  private val maven by mavenFixture()

  @Test
  fun `test get artifact path with illegal newline char in version`() = runBlocking {
    val groupId = "groupId"
    val artifactId = "artifactId"
    val version = "3.1.0"
    val incorrectVersion = "\r\n" + version
    val path = MavenArtifactUtil.getArtifactNioPath(maven.dir, groupId, artifactId, incorrectVersion, "pom").toString()
    assertTrue(path.contains(groupId))
    assertTrue(path.contains(artifactId))
    assertTrue(path.contains(version))
  }

  @Test
  fun `test get artifact path with illegal &rt char in version`() = runBlocking {
    val groupId = "groupId"
    val artifactId = "artifactId"
    val version = "3.1.0"
    val incorrectVersion = ">$version"
    val path = MavenArtifactUtil.getArtifactNioPath(maven.dir, groupId, artifactId, incorrectVersion, "pom").toString()
    assertTrue(path.contains(groupId))
    assertTrue(path.contains(artifactId))
    assertTrue(path.contains(version))
  }

  @Test
  fun `test get artifact path with groupdId of dots`() = runBlocking {
    val groupId = "mygroup.myid"
    val artifactId = "artifact-id"
    val version = "3.1.0"
    val path = MavenArtifactUtil.getArtifactNioPath(maven.dir, groupId, artifactId, version, "pom").toString()
    assertTrue(path.contains("mygroup"))
    assertTrue(path.contains("myid"))
    assertTrue(path.contains(artifactId))
    assertTrue(path.contains(version))
  }
}
