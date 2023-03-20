// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils

import com.intellij.maven.testFramework.MavenTestCase

class FileFinderTest : MavenTestCase() {

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

  fun `test find pom file - expect one main pom xml`() {
    val mainPom = createProjectSubFile("pom.xml", pomContent)
    createProjectSubFile(".flatten-pom.xml", pomContent)
    createProjectSubFile("pom-template.xml", pomContent)
    createProjectSubFile("pom.xml.versionsBackup", pomContent)
    val root = mainPom.parent
    val findPomFiles = FileFinder.findPomFiles(Array(1) { root }, true, mavenProgressIndicator)
    assertTrue(findPomFiles.size == 1)
    assertEquals(mainPom, findPomFiles.get(0))
  }

  fun `test find pom file - expect one main pom yaml`() {
    val mainPom = createProjectSubFile("pom.yaml", pomContent)
    createProjectSubFile(".flatten-pom.xml", pomContent)
    createProjectSubFile("pom-template.xml", pomContent)
    val root = mainPom.parent
    val findPomFiles = FileFinder.findPomFiles(Array(1) { root }, true, mavenProgressIndicator)
    assertTrue(findPomFiles.size == 1)
    assertEquals(mainPom, findPomFiles.get(0))
  }

  fun `test find pom file - old logic many poms`() {
    val pom1 = createProjectSubFile("pom-template.xml", pomContent)
    val pom2 = createProjectSubFile(".flatten-pom.xml", pomContent)
    val root = pom1.parent
    val findPomFiles = FileFinder.findPomFiles(Array(1) { root }, true, mavenProgressIndicator)
    assertTrue(findPomFiles.size == 2)
    assertContainsElements(findPomFiles, pom1, pom2)
  }

  fun `test find pom file - recursion poms`() {
    val mainPom = createProjectSubFile("pom.xml", pomContent)
    createProjectSubFile("pom-template.xml", pomContent)
    val mainPomA = createProjectSubFile("a/pom.xml", pomContent)
    createProjectSubFile("a/pom-template.xml", pomContent)
    val pomB1 = createProjectSubFile("b/pom-template.xml", pomContent)
    val pomB2 = createProjectSubFile("b/.flatten-pom.xml", pomContent)
    createProjectSubFile("c/test.xml", "<test/>")
    val root = mainPom.parent
    val findPomFiles = FileFinder.findPomFiles(Array(1) { root }, true, mavenProgressIndicator)
    assertTrue(findPomFiles.size == 4)
    assertContainsElements(findPomFiles, mainPom, mainPomA, pomB1, pomB2)
  }

  fun `test find pom file with same names - expect one main pom xml`() {
    val mainPom = createProjectSubFile("pom.xml", pomContent)
    createProjectSubFile("pom.png", "not xml")
    createProjectSubFile("pom-all.xml", pomContent)
    createProjectSubFile("pom-2.xml", pomContent)
    val root = mainPom.parent
    val findPomFiles = FileFinder.findPomFiles(Array(1) { root }, true, mavenProgressIndicator)
    assertTrue(findPomFiles.size == 1)
    assertEquals(mainPom, findPomFiles.get(0))
  }
}