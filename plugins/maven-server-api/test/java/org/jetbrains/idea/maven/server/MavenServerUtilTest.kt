// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server

import com.intellij.idea.TestFor
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.io.toCanonicalPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MavenServerUtilTest {

  @Test
  fun testFindMavenBaseDirForSubProject(testInfo: TestInfo) {
    val myTestDir = FileUtilRt.createTempDirectory(testInfo.displayName, "")
    val projectDir = myTestDir.resolve("project")
    val subProjectDir = projectDir.resolve("subproject")
    assertTrue(subProjectDir.mkdirs())
    createMvn(projectDir)
    projectDir.toPath().toCanonicalPath()
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

  @Test
  fun testFindMavenBaseDirForSubProjectIfMaven4SyntaxAndSubProjectIsRoot(testInfo: TestInfo) {
    val myTestDir = FileUtilRt.createTempDirectory(testInfo.displayName, "")
    val projectDir = myTestDir.resolve("project")
    createMvn(projectDir)
    val subProjectDir = projectDir.resolve("subproject")
    assertTrue(subProjectDir.mkdirs())
    FileOutputStream(subProjectDir.resolve("pom.xml")).let { BufferedOutputStream(it) }.use {
      it.write("""<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.1.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.1.0
                             http://maven.apache.org/xsd/maven-4.1.0.xsd" root="true">
  <modelVersion>4.1.0</modelVersion>
  <groupId>test</groupId>
  <artifactId>test</artifactId>
  <version>1.0-SNAPSHOT</version>
  </project>""".trimIndent().toByteArray(Charsets.UTF_8))
    }

    assertSameFile(subProjectDir, MavenServerUtil.findMavenBasedir(subProjectDir))
  }


  @Test
  fun testFindMavenBaseDirForSubProjectIfMaven4SyntaxAndProjectIsRoot(testInfo: TestInfo) {
    val myTestDir = FileUtilRt.createTempDirectory(testInfo.displayName, "")
    val projectDir = myTestDir.resolve("project")
    val subProjectDir = projectDir.resolve("subproject")
    assertTrue(subProjectDir.mkdirs())
    FileOutputStream(projectDir.resolve("pom.xml")).let { BufferedOutputStream(it) }.use {
      it.write("""<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.1.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.1.0
                             http://maven.apache.org/xsd/maven-4.1.0.xsd" root="true">
  <modelVersion>4.1.0</modelVersion>
  <groupId>test</groupId>
  <artifactId>test</artifactId>
  <version>1.0-SNAPSHOT</version>
        
      """.trimIndent().toByteArray(Charsets.UTF_8))
    }

    assertSameFile(projectDir, MavenServerUtil.findMavenBasedir(subProjectDir))
  }

  @Test
  fun testFindMavenBaseDirForSubProjectIfMavenBadSyntax(testInfo: TestInfo) {
    val myTestDir = FileUtilRt.createTempDirectory(testInfo.displayName, "")
    val projectDir = myTestDir.resolve("project")
    createMvn(projectDir)
    val subProjectDir = projectDir.resolve("subproject")
    assertTrue(subProjectDir.mkdirs())
    FileOutputStream(subProjectDir.resolve("pom.xml")).let { BufferedOutputStream(it) }.use {
      it.write("not a pot".trimIndent().toByteArray(Charsets.UTF_8))
    }

    assertSameFile(projectDir, MavenServerUtil.findMavenBasedir(subProjectDir))
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