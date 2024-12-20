// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.actions

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.testFramework.TestActionEvent
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.project.MavenProject
import org.junit.Test

class ReimportProjectActionTest : MavenMultiVersionImportingTestCase() {
  @Test
  fun testReloadOnlyVisibleForNonIgnoredProjects() = runBlocking {
    val parentFile = createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>m1</module>
                  </modules>
                  """.trimIndent())
    val m1File = createModulePom("m1", """
                  <artifactId>m1</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                  </parent>
                  """.trimIndent())
    importProjectAsync()

    val action = ReimportProjectAction()
    val parentActionVisible = action.isVisible(TestActionEvent.createTestEvent(action, createTestDataContext(parentFile)))
    val m1ActionVisible = action.isVisible(TestActionEvent.createTestEvent(action, createTestDataContext(m1File)))

    assertTrue(parentActionVisible)
    assertTrue(m1ActionVisible)

    val module = getModule("m1")
    assertNotNull(module)
    val mavenProject = projectsManager.findProject(module)
    assertFalse(projectsManager.isIgnored(mavenProject!!))
    projectsManager.setIgnoredState(listOf<MavenProject?>(mavenProject), true)
    assertTrue(projectsManager.isIgnored(mavenProject))

    val m1IgnoredActionVisible = action.isVisible(TestActionEvent.createTestEvent(action, createTestDataContext(m1File)))
    assertFalse(m1IgnoredActionVisible)
  }
}
