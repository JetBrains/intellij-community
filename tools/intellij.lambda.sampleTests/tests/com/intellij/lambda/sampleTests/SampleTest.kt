// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lambda.sampleTests

import com.intellij.lambda.testFramework.junit.RunInMonolithAndSplitMode
import com.intellij.lambda.testFramework.utils.IdeWithLambda
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.ex.ProjectManagerEx
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.TestTemplate

@RunInMonolithAndSplitMode
class SampleTest {
  @TestTemplate
  fun `serialized test`(ide: IdeWithLambda) = runBlocking {
    ide.apply {

      runInFrontend {
        Logger.getInstance("test").warn("Projects: " + ProjectManagerEx.getOpenProjects().joinToString { it.name })
      }

      runInBackend {
        Logger.getInstance("test").warn("backend Projects: " + ProjectManagerEx.getOpenProjects().joinToString { it.name })
      }
    }
    Unit
  }

  @TestTemplate
  fun `serialized test failure`(ide: IdeWithLambda) {
    ide.apply {
      val result = runCatching {
        runBlocking {
          runInFrontend {
            Logger.getInstance("test").warn("Projects: " + ProjectManagerEx.getOpenProjects().joinToString { it.name })
            assert(false)
          }

          runInBackend {
            Logger.getInstance("test").warn("backend Projects: " + ProjectManagerEx.getOpenProjects().joinToString { it.name })
          }
        }
      }

      assert(result.isFailure)
      assert(result.exceptionOrNull()?.message?.contains("Assertion failed") == true)
      assert(result.exceptionOrNull()?.stackTraceToString()?.contains("SerializedLambdaHelper") == false)
    }
    Unit
  }

  @TestTemplate
  fun `serialized test with parameter`(ide: IdeWithLambda) = runBlocking {
    ide.apply {
      val text = "Text from backend"
      val returnResult = runInBackendGetResult("Return some text") {
        text
      }

      assert(returnResult is String) { "Expected String, but got ${returnResult::class.java.name}"}
      assert(returnResult == text) { "Expected '$text', but got '$returnResult'" }

      runInFrontend("Print serializable received from backend", listOf(returnResult)) { param ->
        thisLogger().warn("Got parameter: ${param.single()}")
        assert(param.single() == text) { "Expected '$text', but got '${param.single()}'" }
      }
    }
    Unit
  }
}

