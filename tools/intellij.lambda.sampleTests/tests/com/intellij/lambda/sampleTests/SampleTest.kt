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

      run {
        Logger.getInstance("test").warn("Projects: " + ProjectManagerEx.getOpenProjects().joinToString { it.name })
      }

      runInBackend {
        Logger.getInstance("test").warn("backend Projects: " + ProjectManagerEx.getOpenProjects().joinToString { it.name })
      }
    }
    Unit
  }

  @TestTemplate
  fun `serialized test with parameter`(ide: IdeWithLambda) = runBlocking {
    ide.apply {
      val returnResult = runInBackendGetResult("Return some text") {
        "Text from backend"
      }

      run("Print serializable received from backend", listOf(returnResult)) {
        thisLogger().warn("Got parameter: $it")
      }
    }
    Unit
  }
}

