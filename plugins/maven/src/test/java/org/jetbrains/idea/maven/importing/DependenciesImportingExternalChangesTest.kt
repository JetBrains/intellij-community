// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import kotlinx.coroutines.runBlocking
import org.junit.Test

class DependenciesImportingExternalChangesTest : MavenMultiVersionImportingTestCase() {
  override fun setUp() {
    super.setUp()
    projectsManager.initForTests()
    projectsManager.listenForExternalChanges()
  }

  @Test
  fun testUpdateRootEntriesWithActualPath() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <dependencies>
                      <dependency>
                        <groupId>junit</groupId>
                        <artifactId>junit</artifactId>
                        <version>4.0</version>
                      </dependency>
                    </dependencies>
                    """.trimIndent())

    assertProjectLibraries("Maven: junit:junit:4.0")
    assertModuleLibDeps("project", "Maven: junit:junit:4.0")

    assertModuleLibDep("project", "Maven: junit:junit:4.0",
                       "jar://" + repositoryPath + "/junit/junit/4.0/junit-4.0.jar!/",
                       "jar://" + repositoryPath + "/junit/junit/4.0/junit-4.0-sources.jar!/",
                       "jar://" + repositoryPath + "/junit/junit/4.0/junit-4.0-javadoc.jar!/")

    waitForImportWithinTimeout {
      repositoryPath = dir.resolve("__repo").toString()
    }
    projectsManager.embeddersManager.reset() // to recognize repository change

    updateAllProjects()

    assertModuleLibDep("project", "Maven: junit:junit:4.0",
                       "jar://" + repositoryPath + "/junit/junit/4.0/junit-4.0.jar!/",
                       "jar://" + repositoryPath + "/junit/junit/4.0/junit-4.0-sources.jar!/",
                       "jar://" + repositoryPath + "/junit/junit/4.0/junit-4.0-javadoc.jar!/")
  }

  @Test
  fun testUpdateRootEntriesWithActualPathForDependenciesWithClassifiers() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <dependencies>
                      <dependency>
                        <groupId>org.testng</groupId>
                        <artifactId>testng</artifactId>
                        <version>5.8</version>
                        <classifier>jdk15</classifier>
                      </dependency>
                    </dependencies>
                    """.trimIndent())

    assertModuleLibDeps("project", "Maven: org.testng:testng:jdk15:5.8", "Maven: junit:junit:3.8.1")
    assertModuleLibDep("project", "Maven: org.testng:testng:jdk15:5.8",
                       "jar://" + repositoryPath + "/org/testng/testng/5.8/testng-5.8-jdk15.jar!/",
                       "jar://" + repositoryPath + "/org/testng/testng/5.8/testng-5.8-sources.jar!/",
                       "jar://" + repositoryPath + "/org/testng/testng/5.8/testng-5.8-javadoc.jar!/")

    waitForImportWithinTimeout {
      repositoryPath = dir.resolve("__repo").toString()
    }
    projectsManager.embeddersManager.reset() // to recognize repository change

    updateAllProjects()

    assertModuleLibDep("project", "Maven: org.testng:testng:jdk15:5.8",
                       "jar://" + repositoryPath + "/org/testng/testng/5.8/testng-5.8-jdk15.jar!/",
                       "jar://" + repositoryPath + "/org/testng/testng/5.8/testng-5.8-sources.jar!/",
                       "jar://" + repositoryPath + "/org/testng/testng/5.8/testng-5.8-javadoc.jar!/")
  }
}