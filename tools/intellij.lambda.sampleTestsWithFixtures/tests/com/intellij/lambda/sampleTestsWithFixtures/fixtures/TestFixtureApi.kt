// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lambda.sampleTestsWithFixtures.util

import com.intellij.ide.GeneralSettings
import com.intellij.ide.impl.OpenUntrustedProjectChoice
import com.intellij.ide.trustedProjects.impl.TrustedProjectStartupDialog
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.util.Disposer
import com.intellij.remoteDev.tests.LambdaBackendContext
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl

context(lambdaBackendContext: LambdaBackendContext)
suspend fun openNewProjectAndEditor(relativePath: String) {
  val disposable = Disposer.newDisposable("Dialog setup")
  TrustedProjectStartupDialog.setDialogChoiceInTests(OpenUntrustedProjectChoice.TRUST_AND_OPEN, disposable)
  GeneralSettings.getInstance().confirmOpenNewProject = GeneralSettings.OPEN_PROJECT_SAME_WINDOW

  val projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder("Test")
  val codeInsightFixture = CodeInsightTestFixtureImpl(projectBuilder.fixture, TempDirTestFixtureImpl())
  lambdaBackendContext.addAfterEachCleanup {
    codeInsightFixture.tearDown()
    Disposer.dispose(disposable)
  }
  codeInsightFixture.setUp()
  writeAction {
    codeInsightFixture.openFileInEditor(
      codeInsightFixture
        .addFileToProject(
          relativePath,
          // language=java
          """
            package com.example;

            class Foo {
              private boolean unboxedZ = false;
              private byte unboxedB = 0;
              private char unboxedC = 'a';
              private double unboxedD = 0.0;
              private float unboxedF = 0.0f;
              private int unboxedI = 0;
              private long unboxedJ = 0l;
              private short unboxedS = 0;
            }
            """
            .trimIndent(),
        )
        .virtualFile
    )
  }
}
