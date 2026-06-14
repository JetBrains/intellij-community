// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertModuleLibDep
import com.intellij.maven.testFramework.fixtures.assertModuleLibDeps
import com.intellij.maven.testFramework.fixtures.assertProjectLibraries
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.repositoryPathCanonical
import com.intellij.maven.testFramework.fixtures.updateAllProjects
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.fixtures.waitForImportWithinTimeout
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class DependenciesImportingExternalChangesTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  @BeforeEach
  fun setUp() {
    maven.projectsManager.initForTests()
    maven.projectsManager.listenForExternalChanges()
  }

  @Test
  fun testUpdateRootEntriesWithActualPath() = runBlocking {
    maven.importProjectAsync("""
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

    maven.assertProjectLibraries("Maven: junit:junit:4.0")
    maven.assertModuleLibDeps("project", "Maven: junit:junit:4.0")

    maven.assertModuleLibDep("project", "Maven: junit:junit:4.0",
                       "jar://" + maven.repositoryPathCanonical + "/junit/junit/4.0/junit-4.0.jar!/",
                       "jar://" + maven.repositoryPathCanonical + "/junit/junit/4.0/junit-4.0-sources.jar!/",
                       "jar://" + maven.repositoryPathCanonical + "/junit/junit/4.0/junit-4.0-javadoc.jar!/")

    maven.waitForImportWithinTimeout {
      maven.repositoryPath = maven.dir.resolve("__repo")
    }

    maven.updateAllProjects()

    maven.assertModuleLibDep("project", "Maven: junit:junit:4.0",
                       "jar://" + maven.repositoryPathCanonical + "/junit/junit/4.0/junit-4.0.jar!/",
                       "jar://" + maven.repositoryPathCanonical + "/junit/junit/4.0/junit-4.0-sources.jar!/",
                       "jar://" + maven.repositoryPathCanonical + "/junit/junit/4.0/junit-4.0-javadoc.jar!/")
  }

  @Test
  fun testUpdateRootEntriesWithActualPathForDependenciesWithClassifiers() = runBlocking {
    maven.importProjectAsync("""
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

    maven.assertModuleLibDeps("project", "Maven: org.testng:testng:jdk15:5.8", "Maven: junit:junit:3.8.1")
    maven.assertModuleLibDep("project", "Maven: org.testng:testng:jdk15:5.8",
                       "jar://" + maven.repositoryPathCanonical + "/org/testng/testng/5.8/testng-5.8-jdk15.jar!/",
                       "jar://" + maven.repositoryPathCanonical + "/org/testng/testng/5.8/testng-5.8-sources.jar!/",
                       "jar://" + maven.repositoryPathCanonical + "/org/testng/testng/5.8/testng-5.8-javadoc.jar!/")

    maven.waitForImportWithinTimeout {
      maven.repositoryPath = maven.dir.resolve("__repo")
    }

    maven.updateAllProjects()

    maven.assertModuleLibDep("project", "Maven: org.testng:testng:jdk15:5.8",
                       "jar://" + maven.repositoryPathCanonical + "/org/testng/testng/5.8/testng-5.8-jdk15.jar!/",
                       "jar://" + maven.repositoryPathCanonical + "/org/testng/testng/5.8/testng-5.8-sources.jar!/",
                       "jar://" + maven.repositoryPathCanonical + "/org/testng/testng/5.8/testng-5.8-javadoc.jar!/")
  }
}