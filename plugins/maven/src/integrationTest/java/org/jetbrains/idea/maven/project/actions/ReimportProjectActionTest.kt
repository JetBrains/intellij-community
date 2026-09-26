// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.actions

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.createTestDataContext
import com.intellij.maven.testFramework.fixtures.getModule
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.project.MavenProject
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class ReimportProjectActionTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  @Test
  fun testReloadOnlyVisibleForNonIgnoredProjects() = runBlocking {
    val parentFile = maven.createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>m1</module>
                  </modules>
                  """.trimIndent())
    val m1File = maven.createModulePom("m1", """
                  <artifactId>m1</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                  </parent>
                  """.trimIndent())
    maven.importProjectAsync()

    val action = ReimportProjectAction()
    val parentActionVisible = action.isVisible(TestActionEvent.createTestEvent(action, maven.createTestDataContext(parentFile)))
    val m1ActionVisible = action.isVisible(TestActionEvent.createTestEvent(action, maven.createTestDataContext(m1File)))

    assertTrue(parentActionVisible)
    assertTrue(m1ActionVisible)

    val module = maven.getModule("m1")
    assertNotNull(module)
    val mavenProject = maven.projectsManager.findProject(module)
    assertFalse(maven.projectsManager.isIgnored(mavenProject!!))
    maven.projectsManager.setIgnoredState(listOf<MavenProject?>(mavenProject), true)
    assertTrue(maven.projectsManager.isIgnored(mavenProject))

    val m1IgnoredActionVisible = action.isVisible(TestActionEvent.createTestEvent(action, maven.createTestDataContext(m1File)))
    assertFalse(m1IgnoredActionVisible)
  }
}
