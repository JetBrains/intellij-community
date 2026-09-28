// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards

import com.intellij.maven.testFramework.fixtures.createPom
import com.intellij.maven.testFramework.fixtures.createPomXml
import com.intellij.maven.testFramework.fixtures.mavenProjectWizardFixture
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.io.write
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.actions.AddFileAsMavenProjectAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Path

@TestApplication
class MavenAddFileAsMavenProjectActionTest {
  private val maven by mavenProjectWizardFixture()

  @Test
  fun `test import non-default pom`() = runBlocking {
    val pom1: Path = maven.createPom()
    val pom2 = pom1.parent.resolve("pom2.xml")
    pom2.write(createPomXml(
      MavenConstants.MODEL_VERSION_4_0_0,
      """
        <groupId>test</groupId>
        <artifactId>project2</artifactId>
        <version>1</version>
      """.trimIndent(),
      omitModelVersionTag = false))

    val project = maven.project
    val file = StandardFileSystems.local().refreshAndFindFileByPath(pom2.toString())
    val event = TestActionEvent.createTestEvent {
      when {
        CommonDataKeys.PROJECT.`is`(it) -> project
        CommonDataKeys.VIRTUAL_FILE.`is`(it) -> file
        else -> null
      }
    }

    val action = AddFileAsMavenProjectAction()
    action.actionPerformedAsync(event)

    val projectsManager = MavenProjectsManager.getInstance(project)
    val paths = projectsManager.state.originalFiles.map { Path.of(it) }
    assertEquals(1, paths.size)
    assertEquals(pom2, paths[0])
  }
}
