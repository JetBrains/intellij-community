// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server

import com.intellij.idea.TestFor
import com.intellij.openapi.util.io.FileUtilRt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import java.io.File
import java.io.IOException

class MavenServerUtilTest {

  @Test
  fun testFindMavenBaseDirForSubProject(testInfo: TestInfo) {
    val myTestDir = FileUtilRt.createTempDirectory(testInfo.displayName, "")
    val projectDir = myTestDir.resolve("project")
    val subProjectDir = projectDir.resolve("subproject")
    assertTrue(subProjectDir.mkdirs())
    createMvn(projectDir)
    assertSameFile(projectDir, MavenServerUtil.findMavenBasedir(subProjectDir))
  }

  @Test
  fun testFindMavenBaseDirForProject(testInfo: TestInfo) {
    val myTestDir = FileUtilRt.createTempDirectory(testInfo.displayName, "")
    val projectDir = myTestDir.resolve("project")
    assertTrue(projectDir.mkdirs())
    createMvn(projectDir)
    assertSameFile(projectDir, MavenServerUtil.findMavenBasedir(projectDir))
  }

  @Test
  fun testFindSameBaseDirForProjectWhenNoMvn(testInfo: TestInfo) {
    val myTestDir = FileUtilRt.createTempDirectory(testInfo.displayName, "")
    val projectDir = myTestDir.resolve("project")
    assertTrue(projectDir.mkdirs())
    //createMvn(projectDir)
    assertSameFile(projectDir, MavenServerUtil.findMavenBasedir(projectDir))
  }

  @Test
  @TestFor(issues = ["IDEA-377778"])
  fun testFindMavenBaseDirProjectIfParentDirContansMvnAsWell(testInfo: TestInfo) {
    val myTestDir = FileUtilRt.createTempDirectory(testInfo.displayName, "")
    val projectDir = myTestDir.resolve("project")
    assertTrue(projectDir.mkdirs())
    createMvn(projectDir)
    createMvn(myTestDir)
    assertSameFile(projectDir, MavenServerUtil.findMavenBasedir(projectDir))
  }

  private fun assertSameFile(expected: File, actual: File) {
    try {
      assertEquals(expected.canonicalFile, actual.canonicalFile)
    }
    catch (e: IOException) {
      assertEquals(expected.absoluteFile, actual.absoluteFile)
    }
  }


  private fun createMvn(dir: File) {
    dir.resolve(".mvn").mkdirs()
  }
}