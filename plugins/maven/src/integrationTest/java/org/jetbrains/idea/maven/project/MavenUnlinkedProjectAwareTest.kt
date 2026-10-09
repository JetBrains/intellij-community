// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.initProjectsManager
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.projectRoot
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class MavenUnlinkedProjectAwareTest {

  private val maven by mavenImportingFixture()

  @BeforeEach
  fun setUp() {
    maven.initProjectsManager(false)
  }

  @Test
  fun `test project is linked before projects tree is read`() {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())
    val unlinkedProjectAware = MavenUnlinkedProjectAware()
    val projectPath = maven.projectRoot.path

    assertFalse(unlinkedProjectAware.isLinkedProject(maven.project, projectPath))

    maven.projectsManager.addManagedFilesOrUnignoreNoUpdate(listOf(maven.projectPom))

    assertTrue(maven.projectsManager.projects.isEmpty(), "The projects tree must not be read")
    assertTrue(unlinkedProjectAware.isLinkedProject(maven.project, projectPath))
  }
}
