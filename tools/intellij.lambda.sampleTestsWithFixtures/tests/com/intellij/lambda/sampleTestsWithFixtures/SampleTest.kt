// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lambda.sampleTestsWithFixtures

import com.intellij.lambda.sampleTestsWithFixtures.fixtures.codeInsightFixture
import com.intellij.lambda.sampleTestsWithFixtures.fixtures.openNewProjectAndEditor
import com.intellij.lambda.testFramework.junit.RunInMonolithAndSplitMode
import com.intellij.lambda.testFramework.testApi.editor.editorImplOrThrow
import com.intellij.lambda.testFramework.testApi.editor.moveTo
import com.intellij.lambda.testFramework.testApi.editor.typeWithLatency
import com.intellij.lambda.testFramework.testApi.editor.waitContains
import com.intellij.lambda.testFramework.testApi.editor.waitForExpectedSelectedFile
import com.intellij.lambda.testFramework.testApi.getProjects
import com.intellij.lambda.testFramework.testApi.waitForProject
import com.intellij.lambda.testFramework.utils.IdeWithLambda
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.TestTemplate
import kotlin.time.Duration.Companion.seconds

@RunInMonolithAndSplitMode
class SampleTest {
  @TestTemplate
  fun `serialized test`(ide: IdeWithLambda) = runBlocking {
    ide {
      val projectName = "Test"
      val toType = "//123"
      val editorName = "Foo.java"
      // language=java
      val editorContent = """
        package com.example;

        class Foo {
          private boolean unboxedZ = false;
          private byte unboxedB = 0;
          private char unboxedC = 'a';
          private double unboxedD = 0.0;
          private float unboxedF = 0.0f;
          private int unboxedI = 0;
          private long unboxedJ = 0L;
          private short unboxedS = 0;
        }
      """.trimIndent()

      runInBackend("Open project via fixture") {
        codeInsightFixture(projectName).openNewProjectAndEditor("/src/com/example/$editorName", editorContent)
      }

      runInFrontend("Open File in Project") {
        waitForExpectedSelectedFile(editorName, project = waitForProject(projectName)).editorImplOrThrow.apply {
          moveTo(2, 1)
          typeWithLatency(toType)
        }
      }

      runInBackend("Check typed on frontend") {
        waitForExpectedSelectedFile(editorName, project = waitForProject("Test")).editorImplOrThrow.apply {
          waitContains(toType, 5.seconds)
        }
      }
    }
  }

  @TestTemplate
  fun serialized(ide: IdeWithLambda) = runBlocking {
    ide {
      runInBackend("get projects") {
        Logger.getInstance("test").warn("Projects: " + getProjects().joinToString { it.name })
      }
      runInFrontend("get projects") {
        Logger.getInstance("test").warn("Projects: " + getProjects().joinToString { it.name })
      }
    }
  }
}

