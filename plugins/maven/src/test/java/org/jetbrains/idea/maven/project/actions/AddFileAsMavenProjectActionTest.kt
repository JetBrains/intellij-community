// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project.actions

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.TestActionEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.maven.fixtures.MavenVersionArguments
import org.jetbrains.idea.maven.fixtures.assertModules
import org.jetbrains.idea.maven.fixtures.createPomXml
import org.jetbrains.idea.maven.fixtures.createProjectPom
import org.jetbrains.idea.maven.fixtures.mavenImportingFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class AddFileAsMavenProjectActionTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  

  @Test
  fun testFilesSavedOnAction() = runBlocking {
    val projectPom = maven.createProjectPom("<groupId>test</groupId>" +
                                      "<artifactId>project</artifactId>" +
                                      "<version>1</version>")

    val document = readAction {
      val psiFile = PsiManager.getInstance(maven.project).findFile(projectPom)!!
      PsiDocumentManager.getInstance(maven.project).getDocument(psiFile)!!
    }

    // make a change but do not save
    edtWriteAction {
      document.setText(maven.createPomXml("<groupId>test</groupId>" +
                                    "<artifactId>project-new</artifactId>" +
                                    "<version>1</version>"))
    }

    val context = MapDataContext()
    context.put(CommonDataKeys.PROJECT, maven.project)
    context.put(CommonDataKeys.VIRTUAL_FILE, projectPom)
    val event = TestActionEvent.createTestEvent(context)
    withContext(Dispatchers.EDT) {
      AddFileAsMavenProjectAction().actionPerformedAsync(event)
    }

    maven.assertModules("project-new")
  }

}