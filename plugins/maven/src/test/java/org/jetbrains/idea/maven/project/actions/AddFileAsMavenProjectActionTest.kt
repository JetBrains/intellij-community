// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project.actions

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.WriteAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.TestActionEvent
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import org.junit.Test

class AddFileAsMavenProjectActionTest : MavenMultiVersionImportingTestCase() {
  @Test
  fun testFilesSavedOnAction() {
    val projectPom = createProjectPom("<groupId>test</groupId>" +
                                            "<artifactId>project</artifactId>" +
                                            "<version>1</version>")

    val psiFile = PsiManager.getInstance(myProject).findFile(projectPom)!!
    val document = PsiDocumentManager.getInstance(myProject)
      .getDocument(psiFile)!!

    // make a change but do not save
    WriteAction.run<Throwable> {
      document.setText(createPomXml("<groupId>test</groupId>" +
                       "<artifactId>project-new</artifactId>" +
                       "<version>1</version>"))
    }

    val context = MapDataContext()
    context.put(CommonDataKeys.PROJECT, myProject)
    context.put(CommonDataKeys.VIRTUAL_FILE, projectPom)
    val event = TestActionEvent(context)
    AddFileAsMavenProjectAction().actionPerformed(event)

    val promise = myProjectsManager.waitForImportCompletion()
    myProjectsManager.performScheduledImportInTests()
    assertTrue("Import did not succeed", promise.isSucceeded)
    assertModules("project-new")
  }

}