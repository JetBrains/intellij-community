// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils

import com.intellij.maven.testFramework.MavenTestCase
import junit.framework.TestCase
import kotlinx.coroutines.runBlocking
import org.junit.Test

class MavenArtifactUtilTest : MavenTestCase() {
  @Test
  fun `test get artifact path with illegal newline char in version`() = runBlocking {
    val groupId = "groupId"
    val artifactId = "artifactId"
    val version = "3.1.0"
    val incorrectVersion = "\r\n" + version
    val path = MavenArtifactUtil.getArtifactNioPath(dir.toPath(), groupId, artifactId, incorrectVersion, "pom").toString()
    TestCase.assertTrue(path.contains(groupId))
    TestCase.assertTrue(path.contains(artifactId))
    TestCase.assertTrue(path.contains(version))
  }

  @Test
  fun `test get artifact path with illegal &rt char in version`() = runBlocking {
    val groupId = "groupId"
    val artifactId = "artifactId"
    val version = "3.1.0"
    val incorrectVersion = ">$version"
    val path = MavenArtifactUtil.getArtifactNioPath(dir.toPath(), groupId, artifactId, incorrectVersion, "pom").toString()
    TestCase.assertTrue(path.contains(groupId))
    TestCase.assertTrue(path.contains(artifactId))
    TestCase.assertTrue(path.contains(version))
  }

  @Test
  fun `test get artifact path with groupdId of dots`() = runBlocking {
    val groupId = "mygroup.myid"
    val artifactId = "artifact-id"
    val version = "3.1.0"
    val path = MavenArtifactUtil.getArtifactNioPath(dir.toPath(), groupId, artifactId, version, "pom").toString()
    TestCase.assertTrue(path.contains("mygroup"))
    TestCase.assertTrue(path.contains("myid"))
    TestCase.assertTrue(path.contains(artifactId))
    TestCase.assertTrue(path.contains(version))
  }
}