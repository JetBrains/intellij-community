// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.testFramework.utils.io.deleteRecursively
import kotlinx.coroutines.runBlocking
import kotlin.io.path.exists
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.maven.fixtures.MavenVersionArguments
import org.jetbrains.idea.maven.fixtures.assertModules
import org.jetbrains.idea.maven.fixtures.assumeModel_4_1_0
import org.jetbrains.idea.maven.fixtures.createProjectSubDir
import org.jetbrains.idea.maven.fixtures.createProjectSubFile
import org.jetbrains.idea.maven.fixtures.importProjectAsync
import org.jetbrains.idea.maven.fixtures.mavenImportingFixture
import org.jetbrains.idea.maven.fixtures.projectPath
import org.jetbrains.idea.maven.fixtures.projectsTree
import org.jetbrains.idea.maven.fixtures.setRawPomFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

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
