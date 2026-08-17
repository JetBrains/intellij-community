// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils

import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.mavenFixture
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.testFramework.UsefulTestCase.assertContainsElements
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class FileFinderTest {
  private val maven by mavenFixture()

  private val pomContent = """
                  <project xmlns="http://maven.apache.org/POM/4.0.0"
                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                            xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                            http://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <groupId>test</groupId>
                    <artifactId>p1</artifactId>
                    <version>1</version>
                    <modelVersion>4.0.0</modelVersion>
                  </project>"""

  @Test
  fun `test find pom file - expect one main pom xml`() {
    val mainPom = maven.createProjectSubFile("pom.xml", pomContent)
    maven.createProjectSubFile(".flatten-pom.xml", pomContent)
    maven.createProjectSubFile("pom-template.xml", pomContent)
    maven.createProjectSubFile("pom.xml.versionsBackup", pomContent)
    val root = mainPom.parent
    val findPomFiles = FileFinder.findPomFiles(Array(1) { root }, true, null as ProgressIndicator?)
    assertTrue(findPomFiles.size == 1)
    assertEquals(mainPom, findPomFiles.get(0))
  }

  @Test
  fun `test find pom file - expect one main pom yaml`() {
    val mainPom = maven.createProjectSubFile("pom.yaml", pomContent)
    maven.createProjectSubFile(".flatten-pom.xml", pomContent)
    maven.createProjectSubFile("pom-template.xml", pomContent)
    val root = mainPom.parent
    val findPomFiles = FileFinder.findPomFiles(Array(1) { root }, true, null as ProgressIndicator?)
    assertTrue(findPomFiles.size == 1)
    assertEquals(mainPom, findPomFiles.get(0))
  }

  @Test
  fun `test find pom file - old logic many poms`() {
    val pom1 = maven.createProjectSubFile("pom-template.xml", pomContent)
    val pom2 = maven.createProjectSubFile(".flatten-pom.xml", pomContent)
    val root = pom1.parent
    val findPomFiles = FileFinder.findPomFiles(Array(1) { root }, true, null as ProgressIndicator?)
    assertTrue(findPomFiles.size == 2)
    assertContainsElements(findPomFiles, pom1, pom2)
  }

  @Test
  fun `test find pom file - recursion poms`() {
    val mainPom = maven.createProjectSubFile("pom.xml", pomContent)
    maven.createProjectSubFile("pom-template.xml", pomContent)
    val mainPomA = maven.createProjectSubFile("a/pom.xml", pomContent)
    maven.createProjectSubFile("a/pom-template.xml", pomContent)
    val pomB1 = maven.createProjectSubFile("b/pom-template.xml", pomContent)
    val pomB2 = maven.createProjectSubFile("b/.flatten-pom.xml", pomContent)
    maven.createProjectSubFile("c/test.xml", "<test/>")
    val root = mainPom.parent
    val findPomFiles = FileFinder.findPomFiles(Array(1) { root }, true, null as ProgressIndicator?)
    assertTrue(findPomFiles.size == 4)
    assertContainsElements(findPomFiles, mainPom, mainPomA, pomB1, pomB2)
  }

  @Test
  fun `test find pom file with same names - expect one main pom xml`() {
    val mainPom = maven.createProjectSubFile("pom.xml", pomContent)
    maven.createProjectSubFile("pom.png", "not xml")
    maven.createProjectSubFile("pom-all.xml", pomContent)
    maven.createProjectSubFile("pom-2.xml", pomContent)
    val root = mainPom.parent
    val findPomFiles = FileFinder.findPomFiles(Array(1) { root }, true, null as ProgressIndicator?)
    assertTrue(findPomFiles.size == 1)
    assertEquals(mainPom, findPomFiles.get(0))
  }
}
