// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.testFramework.utils.io.deleteRecursively
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.io.path.exists

internal class MavenSubprojectImportingTest : MavenMultiVersionImportingTestCase() {


  @Test
  fun testImportSubprojectWithSetRoot() = runBlocking {
    assumeModel_4_1_0("only for 4.1.0")
    ensureNoDotMvn()
    setRawPomFile("""<?xml version="1.0"?>
      <project xmlns="http://maven.apache.org/POM/4.1.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.1.0 http://maven.apache.org/xsd/maven-4.1.0.xsd" root="true">
        <modelVersion>4.1.0</modelVersion>
        <groupId>group</groupId>
        <artifactId>artifact</artifactId>
        <version>1.0</version>
        </project>
    """.trimIndent())

    createProjectSubFile("../pom.xml",
                         """
        <project xmlns="http://maven.apache.org/POM/4.1.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.1.0 http://maven.apache.org/xsd/maven-4.1.0.xsd">
        <modelVersion>4.1.0</modelVersion>
        <groupId>parent</groupId>
        <artifactId>parent</artifactId>
        <version>1.0</version>
        </project>
      """.trimIndent())
    createProjectSubDir("../.mvn")
    importProjectAsync()
    assertModules("artifact")
    assertEquals("should be exactly 1 project", 1, projectsTree.projects.size)
    assertEquals("group:artifact:1.0", projectsTree.projects[0].mavenId.toString())
  }

  @Test
  fun testImportSubprojectWithOldModelAndMisconfiguredRoot() = runBlocking {
    ensureNoDotMvn()

    createProjectSubFile("../pom.xml",
                         """
        <project xmlns="http://maven.apache.org/POM/4.1.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.1.0 http://maven.apache.org/xsd/maven-4.1.0.xsd">
        <modelVersion>4.1.0</modelVersion>
        <groupId>parent</groupId>
        <artifactId>parent</artifactId>
        <version>1.0</version>
        </project>
      """.trimIndent())
    createProjectSubDir("../.mvn")
    importProjectAsync("""
       <groupId>group</groupId>
        <artifactId>artifact</artifactId>
        <version>1.0</version>""")
    assertModules("artifact")
    assertEquals("should be exactly 1 project", 1, projectsTree.projects.size)
    assertEquals("group:artifact:1.0", projectsTree.projects[0].mavenId.toString())
  }

  private fun ensureNoDotMvn() {
    projectPath.resolve(".mvn").deleteRecursively()
    assertFalse("There should not be .mvn dir", projectPath.resolve(".mvn").exists())
  }
}
