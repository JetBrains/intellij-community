// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards

import com.intellij.maven.testFramework.MavenTestCase
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.TestActionEvent
import com.intellij.util.io.write
import junit.framework.TestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.actions.AddFileAsMavenProjectAction
import java.nio.file.Path

class MavenAddFileAsMavenProjectActionTest : MavenProjectWizardTestCase() {
  fun `test import non-default pom`() = runBlocking {
    val pom1: Path = createPom()
    val pom2 = pom1.parent.resolve("pom2.xml")
    pom2.write(MavenTestCase.createPomXml(
     MavenConstants.MODEL_VERSION_4_0_0,
      """
        <groupId>test</groupId>
        <artifactId>project2</artifactId>
        <version>1</version>
        """.trimIndent()))

    val file = LocalFileSystem.getInstance().refreshAndFindFileByPath(pom2.toString())
    val event = TestActionEvent.createTestEvent {
      when {
        CommonDataKeys.PROJECT.`is`(it) -> project
        CommonDataKeys.VIRTUAL_FILE.`is`(it) -> file
        else -> null
      }
    }

    val action = AddFileAsMavenProjectAction()
    action.actionPerformedAsync(event)

    val projectsManager = MavenProjectsManager.getInstance(module.project)
    val paths = projectsManager.projectsTreeForTests.existingManagedFiles.map { it.toNioPath() }
    TestCase.assertEquals(1, paths.size)
    TestCase.assertEquals(pom2, paths[0])
  }
}