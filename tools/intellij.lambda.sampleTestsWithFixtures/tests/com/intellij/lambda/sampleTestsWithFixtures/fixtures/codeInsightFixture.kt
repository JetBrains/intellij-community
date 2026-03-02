// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lambda.sampleTestsWithFixtures.fixtures

import com.intellij.ide.GeneralSettings
import com.intellij.ide.impl.OpenUntrustedProjectChoice
import com.intellij.ide.trustedProjects.impl.TrustedProjectStartupDialog
import com.intellij.openapi.application.writeAction
import com.intellij.remoteDev.tests.LambdaBackendContext
import com.intellij.remoteDev.tests.LambdaIdeContext
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl

context(context: LambdaIdeContext)
fun codeInsightFixture(projectName: String = "Test"): CodeInsightTestFixtureImpl =
  newTestFixture<CodeInsightTestFixtureImpl> {
    val projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(projectName)
    CodeInsightTestFixtureImpl(projectBuilder.fixture, TempDirTestFixtureImpl())
  }

context(lambdaBackendContext: LambdaBackendContext)
suspend fun CodeInsightTestFixtureImpl.openNewProjectAndEditor(relativePath: String, fileContent: String) {
  TrustedProjectStartupDialog.setDialogChoiceInTests(OpenUntrustedProjectChoice.TRUST_AND_OPEN, lambdaBackendContext.globalDisposable)

  val savedConfirmation = GeneralSettings.getInstance().confirmOpenNewProject
  GeneralSettings.getInstance().confirmOpenNewProject = GeneralSettings.OPEN_PROJECT_SAME_WINDOW
  lambdaBackendContext.addAfterEachCleanup {
    GeneralSettings.getInstance().confirmOpenNewProject = savedConfirmation
  }

  writeAction {
    openFileInEditor(
      addFileToProject(relativePath, fileContent).virtualFile
    )
  }
}
