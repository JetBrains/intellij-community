// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lambda.sampleTestsWithFixtures.util

import com.intellij.openapi.application.writeAction
import com.intellij.remoteDev.tests.LambdaIdeContext
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job

context(lambdaIdeContext: LambdaIdeContext)
suspend fun openNewEditor(relativePath: String) {
  val projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder("Test")
  val codeInsightFixture = CodeInsightTestFixtureImpl(projectBuilder.fixture, TempDirTestFixtureImpl())
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
  currentCoroutineContext().job.invokeOnCompletion { codeInsightFixture.tearDown() }
}
