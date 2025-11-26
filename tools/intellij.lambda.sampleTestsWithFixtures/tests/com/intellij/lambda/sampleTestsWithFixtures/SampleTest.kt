// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lambda.sampleTestsWithFixtures

import com.intellij.lambda.sampleTestsWithFixtures.util.openNewEditor
import com.intellij.lambda.testFramework.junit.RunInMonolithAndSplitMode
import com.intellij.lambda.testFramework.testApi.editor.openFile
import com.intellij.lambda.testFramework.testApi.getProject
import com.intellij.lambda.testFramework.testApi.getProjects
import com.intellij.lambda.testFramework.utils.BackgroundRunWithLambda
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.TestTemplate

@RunInMonolithAndSplitMode
class SampleTest {
  @TestTemplate
  fun `serialized test`(ide: BackgroundRunWithLambda) = runBlocking {
    ide.apply {
      runInBackend {
        //waitForProject(20.seconds)
      }

      run {
        openNewEditor("/src/com/example/Foo.java")
        Logger.getInstance("test").warn("Projects: " + getProjects().joinToString { it.name })
      }

      runInBackend {
        Logger.getInstance("test").warn("backend Projects: " + getProject())
        openFile("src/com/example/Foo.java", waitForReadyState = false, requireFocus = false)
      }
    }
    Unit
  }
}

