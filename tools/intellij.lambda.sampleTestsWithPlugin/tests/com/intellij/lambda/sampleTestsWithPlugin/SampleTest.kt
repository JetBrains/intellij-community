// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lambda.sampleTestsWithPlugin

import com.intellij.lambda.testFramework.junit.RunInMonolithAndSplitMode
import com.intellij.lambda.testFramework.starter.UltimateTestCases.JpsEmptyProject
import com.intellij.lambda.testFramework.testApi.editor.openFile
import com.intellij.lambda.testFramework.testApi.getProject
import com.intellij.lambda.testFramework.testApi.getProjects
import com.intellij.lambda.testFramework.testApi.waitForProject
import com.intellij.lambda.testFramework.utils.IdeWithLambda
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.ProjectManager
import com.intellij.remoteDev.tests.modelGenerated.LambdaRdIdeType
import com.intellij.util.io.createDirectories
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assumptions
import org.junit.jupiter.api.TestTemplate
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.Serializable
import java.util.stream.Stream
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.seconds

@RunInMonolithAndSplitMode
class SampleTest {
  @TestTemplate
  fun `serialized test`(ide: IdeWithLambda) = runBlocking {
    Assumptions.assumeThat(ide.rdSession.rdIdeType)
      .describedAs("works in both modes if headless is turned off for monolith in com.intellij.lambda.testFramework.starter.NewContextWithLambdaKt.newContextWithLambda" +
                   "as ProjectManager returns empty projects list in headless IJPL-221229")
      // TODO: https://youtrack.jetbrains.com/issue/AT-3645/Lambda-tests-possibility-to-use-RunInMonolithAndSplitMode-annotation-on-test-methods
      .isNotIn(LambdaRdIdeType.MONOLITH)
    JpsEmptyProject.projectInfo.projectDir.resolve("src").resolve("FormattingExamplesExpected.java").let {
      if (!it.exists()) {
        it.parent.createDirectories()
        it.createFile()
      }
    }
    ide.apply {
      runInBackend {
        waitForProject(20.seconds)
      }

      run {
        Logger.getInstance("test").warn("Projects: " + getProjects().joinToString { it.name })
      }

      runInBackend {
        Logger.getInstance("test").warn("backend Projects: " + getProject())
        openFile("src/FormattingExamplesExpected.java", waitForReadyState = false, requireFocus = false)
      }
    }
    Unit
  }


  private fun simpleParamProvider(): Stream<Arguments> {
    return Stream.of(
      Arguments.of(1, "one"),
      Arguments.of(2, "two"),
    )
  }

  @ParameterizedTest
  @MethodSource("simpleParamProvider")
  // BackgroundRunWithLambda must be the last parameter
  fun `simple parameterized test`(param: Int, str: String, ide: IdeWithLambda) = runBlocking {
    ide.apply {
      run {
        Logger.getInstance("test")
          .warn("Param: $param $str Projects: " + ProjectManager.getInstance().getOpenProjects().joinToString { it.name })
      }

      runInBackend {
        Logger.getInstance("test")
          .warn("Param: $param $str Backend Projects: " + ProjectManager.getInstance().getOpenProjects().joinToString { it.name })
      }
    }
    Unit
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
    ide.apply {
      run {
        Logger.getInstance("test")
          .warn("Param: $param Projects: " + ProjectManager.getInstance().getOpenProjects().joinToString { it.name })
      }

      runInBackend {
        Logger.getInstance("test")
          .warn("Param: $param Backend Projects: " + ProjectManager.getInstance().getOpenProjects().joinToString { it.name })
      }
    }
    Unit
  }
}

