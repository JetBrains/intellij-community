// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project.actions

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.TestActionEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Test

class AddFileAsMavenProjectActionTest : MavenMultiVersionImportingTestCase() {

  @Test
  fun testFilesSavedOnAction() = runBlocking {
    val projectPom = createProjectPom("<groupId>test</groupId>" +
                                      "<artifactId>project</artifactId>" +
                                      "<version>1</version>")

    val document = readAction {
      val psiFile = PsiManager.getInstance(project).findFile(projectPom)!!
      PsiDocumentManager.getInstance(project).getDocument(psiFile)!!
    }

    // make a change but do not save
    writeAction {
      document.setText(createPomXml("<groupId>test</groupId>" +
                                    "<artifactId>project-new</artifactId>" +
                                    "<version>1</version>"))
    }

    val context = MapDataContext()
    context.put(CommonDataKeys.PROJECT, project)
    context.put(CommonDataKeys.VIRTUAL_FILE, projectPom)
    val event = TestActionEvent.createTestEvent(context)
    withContext(Dispatchers.EDT) {
      AddFileAsMavenProjectAction().actionPerformedAsync(event)
    }

    assertModules("project-new")
  }

}