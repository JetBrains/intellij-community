// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.assumeModel_4_1_0
import com.intellij.maven.testFramework.fixtures.createProjectSubDir
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.projectPath
import com.intellij.maven.testFramework.fixtures.projectsTree
import com.intellij.maven.testFramework.fixtures.setRawPomFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.utils.io.deleteRecursively
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import kotlin.io.path.exists

internal @TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenSubprojectImportingTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  


  @Test
  fun testImportSubprojectWithSetRoot() = runBlocking {
    maven.assumeModel_4_1_0("only for 4.1.0")
    ensureNoDotMvn()
    maven.setRawPomFile("""<?xml version="1.0"?>
      <project xmlns="http://maven.apache.org/POM/4.1.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.1.0 http://maven.apache.org/xsd/maven-4.1.0.xsd" root="true">
        <modelVersion>4.1.0</modelVersion>
        <groupId>group</groupId>
        <artifactId>artifact</artifactId>
        <version>1.0</version>
        </project>
    """.trimIndent())

    maven.createProjectSubFile("../pom.xml",
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
    maven.createProjectSubDir("../.mvn")
    maven.importProjectAsync()
    maven.assertModules("artifact")
    assertEquals(1, maven.projectsTree.projects.size, "should be exactly 1 project")
    assertEquals("group:artifact:1.0", maven.projectsTree.projects[0].mavenId.toString())
  }

  @Test
  fun testImportSubprojectWithOldModelAndMisconfiguredRoot() = runBlocking {
    ensureNoDotMvn()

    maven.createProjectSubFile("../pom.xml",
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
    maven.createProjectSubDir("../.mvn")
    maven.importProjectAsync("""
       <groupId>group</groupId>
        <artifactId>artifact</artifactId>
        <version>1.0</version>""")
    maven.assertModules("artifact")
    assertEquals(1, maven.projectsTree.projects.size, "should be exactly 1 project")
    assertEquals("group:artifact:1.0", maven.projectsTree.projects[0].mavenId.toString())
  }

  private fun ensureNoDotMvn() {
    maven.projectPath.resolve(".mvn").deleteRecursively()
    assertFalse(maven.projectPath.resolve(".mvn").exists(), "There should not be .mvn dir")
  }
}
