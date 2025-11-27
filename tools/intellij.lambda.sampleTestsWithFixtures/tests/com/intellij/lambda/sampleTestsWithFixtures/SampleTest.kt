// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lambda.sampleTestsWithFixtures

import com.intellij.lambda.sampleTestsWithFixtures.util.openNewProjectAndEditor
import com.intellij.lambda.testFramework.junit.RunInMonolithAndSplitMode
import com.intellij.lambda.testFramework.testApi.editor.editorImplOrThrow
import com.intellij.lambda.testFramework.testApi.editor.moveTo
import com.intellij.lambda.testFramework.testApi.editor.typeWithLatency
import com.intellij.lambda.testFramework.testApi.editor.waitForExpectedSelectedFile
import com.intellij.lambda.testFramework.testApi.getProjects
import com.intellij.lambda.testFramework.testApi.waitForProject
import com.intellij.lambda.testFramework.utils.BackgroundRunWithLambda
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.TestTemplate

@RunInMonolithAndSplitMode
class SampleTest {
  @TestTemplate
  fun `serialized test`(ide: BackgroundRunWithLambda) = runBlocking {
    ide.apply {
      try {
        val toType = "//123"
        val editorName = "Foo.java"

        runInBackend("Open project via fixture") {
          openNewProjectAndEditor("/src/com/example/$editorName")
        }

        run("Open File in Project") {
          waitForExpectedSelectedFile(editorName, project = waitForProject("Test")).editorImplOrThrow.apply {
            moveTo(2, 1)
            typeWithLatency(toType)
          }
        }

        runInBackend("Check typed on frontend") {
          waitForExpectedSelectedFile(editorName, project = waitForProject("Test")).editorImplOrThrow.apply {
            assert(document.text.contains(toType))
          }
        }
      }
      finally {
        cleanUp()
      }
    }

    Unit
  }

  @TestTemplate
  fun serialized(ide: BackgroundRunWithLambda) = runBlocking {
    ide.apply {
      runInBackend("get projects") {
        Logger.getInstance("test").warn("Projects: " + getProjects().joinToString { it.name })
      }
      run("get projects") {
        Logger.getInstance("test").warn("Projects: " + getProjects().joinToString { it.name })
      }
      cleanUp()
    }
    Unit
  }
}

