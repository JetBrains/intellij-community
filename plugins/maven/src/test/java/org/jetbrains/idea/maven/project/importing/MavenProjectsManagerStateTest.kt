// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.junit.Test

class MavenProjectsManagerStateTest : MavenMultiVersionImportingTestCase() {
  override fun setUp() {
    super.setUp()
    initProjectsManager(true)
  }

  @Test
  fun testSavingAndLoadingState() = runBlocking {
    var state = projectsManager.getState()
    assertTrue(state.originalFiles.isEmpty())
    assertTrue(state.enabledProfiles.isEmpty())
    assertTrue(state.ignoredFiles.isEmpty())
    assertTrue(state.ignoredPathMasks.isEmpty())

    val p1 = createModulePom("project1",
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

    val p2 = createModulePom("project2",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>project2</artifactId>
                                       <version>1</version>
                                       <packaging>pom</packaging>
                                       <modules>
                                         <module>../project3</module>
                                       </modules>
                                       """.trimIndent())

    val p3 = createModulePom("project3",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>project3</artifactId>
                                       <version>1</version>
                                       """.trimIndent())

    importProjectsAsync(p1, p2)
    projectsManager.explicitProfiles = MavenExplicitProfiles(mutableListOf("one", "two"))
    setIgnoredFilesPathForNextImport(listOf(p1.getPath()))
    setIgnoredPathPatternsForNextImport(mutableListOf<String?>("*.xxx"))

    state = projectsManager.getState()
    assertUnorderedPathsAreEqual(state.originalFiles, listOf(p1.getPath(), p2.getPath()))
    assertUnorderedElementsAreEqual(state.enabledProfiles, "one", "two")
    assertUnorderedPathsAreEqual(state.ignoredFiles, listOf(p1.getPath()))
    assertUnorderedElementsAreEqual(state.ignoredPathMasks, "*.xxx")
  }
}
