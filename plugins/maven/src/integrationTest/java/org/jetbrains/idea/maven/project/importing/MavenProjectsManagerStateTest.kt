// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.importing

import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertUnorderedElementsAreEqual
import com.intellij.maven.testFramework.fixtures.assertUnorderedPathsAreEqual
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.importProjectsAsync
import com.intellij.maven.testFramework.fixtures.initProjectsManager
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.setIgnoredFilesPathForNextImport
import com.intellij.maven.testFramework.fixtures.setIgnoredPathPatternsForNextImport
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenProjectsManagerStateTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  @BeforeEach
  fun setUp() {
    maven.initProjectsManager(true)
  }

  @Test
  fun testSavingAndLoadingState() = runBlocking {
    var state = maven.projectsManager.getState()
    assertTrue(state.originalFiles.isEmpty())
    assertTrue(state.enabledProfiles.isEmpty())
    assertTrue(state.ignoredFiles.isEmpty())
    assertTrue(state.ignoredPathMasks.isEmpty())

    val p1 = maven.createModulePom("project1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>project1</artifactId>
                                       <version>1</version>
                                       <profiles>
                                        <profile>
                                         <id>one</id>
                                        </profile>
                                        <profile>
                                         <id>two</id>
                                        </profile>
                                        <profile>
                                         <id>three</id>
                                        </profile>
                                       </profiles>
                                       """.trimIndent())

    val p2 = maven.createModulePom("project2",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>project2</artifactId>
                                       <version>1</version>
                                       <packaging>pom</packaging>
                                       <modules>
                                         <module>../project3</module>
                                       </modules>
                                       """.trimIndent())

    val p3 = maven.createModulePom("project3",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>project3</artifactId>
                                       <version>1</version>
                                       """.trimIndent())

    maven.importProjectsAsync(p1, p2)
    maven.projectsManager.explicitProfiles = MavenExplicitProfiles(mutableListOf("one", "two"))
    maven.setIgnoredFilesPathForNextImport(listOf(p1.getPath()))
    maven.setIgnoredPathPatternsForNextImport(mutableListOf<String?>("*.xxx"))

    state = maven.projectsManager.getState()
    assertUnorderedPathsAreEqual(state.originalFiles, listOf(p1.getPath(), p2.getPath()))
    assertUnorderedElementsAreEqual(state.enabledProfiles, "one", "two")
    assertUnorderedPathsAreEqual(state.ignoredFiles, listOf(p1.getPath()))
    assertUnorderedElementsAreEqual(state.ignoredPathMasks, "*.xxx")
  }
}
