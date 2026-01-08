// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lambda.sampleTestsWithFixtures

import com.intellij.lambda.sampleTestsWithFixtures.fixtures.codeInsightFixture
import com.intellij.lambda.sampleTestsWithFixtures.fixtures.openNewProjectAndEditor
import com.intellij.lambda.testFramework.junit.RunInMonolithAndSplitMode
import com.intellij.lambda.testFramework.testApi.editor.*
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
      val toType = "//123"
      val editorName = "Foo.java"

      runInBackend("Open project via fixture") {
        codeInsightFixture.openNewProjectAndEditor("/src/com/example/$editorName")
      }

      runInFrontend("Open File in Project") {
        waitForExpectedSelectedFile(editorName, project = waitForProject("Test")).editorImplOrThrow.apply {
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

