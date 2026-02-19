// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lambda.sampleTestsWithPlugin

import com.intellij.lambda.testFramework.junit.RunInMonolithAndSplitMode
import com.intellij.lambda.testFramework.junit.WithProject
import com.intellij.lambda.testFramework.project.HelloWorldProject
import com.intellij.lambda.testFramework.project.TestAppProject
import com.intellij.lambda.testFramework.testApi.editor.openFile
import com.intellij.lambda.testFramework.testApi.getProject
import com.intellij.lambda.testFramework.testApi.getProjects
import com.intellij.lambda.testFramework.utils.IdeWithLambda
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.ProjectManager
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.TestTemplate
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.Serializable
import java.util.stream.Stream

@RunInMonolithAndSplitMode
class SampleTest {
  @WithProject(TestAppProject::class)
  @TestTemplate
  fun `serialized test`(ide: IdeWithLambda) = runBlocking {
    ide {
      runInFrontend {
        Logger.getInstance("test").warn("Projects: " + getProjects().joinToString { it.name })
      }

      runInBackend {
        Logger.getInstance("test").warn("backend Projects: " + getProject())
        openFile("src/SomeClass.java", waitForReadyState = false, requireFocus = false)
      }
    }
  }


  private fun simpleParamProvider(): Stream<Arguments> {
    return Stream.of(
      Arguments.of(1, "one"),
      Arguments.of(2, "two"),
    )
  }

  @ParameterizedTest
  @MethodSource("simpleParamProvider")
  @WithProject(HelloWorldProject::class)
  // BackgroundRunWithLambda must be the last parameter
  fun `simple parameterized test`(param: Int, str: String, ide: IdeWithLambda) = runBlocking {
    ide {
      runInFrontend {
        Logger.getInstance("test")
          .warn("Param: $param $str Projects: " + ProjectManager.getInstance().getOpenProjects().joinToString { it.name })
      }

      runInBackend {
        Logger.getInstance("test")
          .warn("Param: $param $str Backend Projects: " + ProjectManager.getInstance().getOpenProjects().joinToString { it.name })
      }
    }
  }

  data class CustomParam(val param: Int, val str: String) : Serializable

  private fun customParamProvider(): Stream<CustomParam> {
    return Stream.of(
      CustomParam(1, "one"),
      CustomParam(2, "two")
    )
  }

  @ParameterizedTest
  @MethodSource("customParamProvider")
  // BackgroundRunWithLambda must be the last parameter
  fun `custom parameterized test`(param: CustomParam, ide: IdeWithLambda) = runBlocking {
    ide {
      runInFrontend {
        Logger.getInstance("test")
          .warn("Param: $param Projects: " + ProjectManager.getInstance().getOpenProjects().joinToString { it.name })
      }

      runInBackend {
        Logger.getInstance("test")
          .warn("Param: $param Backend Projects: " + ProjectManager.getInstance().getOpenProjects().joinToString { it.name })
      }
    }
  }
}

