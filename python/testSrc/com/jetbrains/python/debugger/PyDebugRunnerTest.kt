// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger

import com.intellij.execution.target.local.LocalTargetEnvironment
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.execution.target.value.constant
import com.jetbrains.python.run.PythonScriptExecution
import org.assertj.core.api.AutoCloseableSoftAssertions
import org.junit.Test

class PyDebugRunnerTest {
  @Test
  fun testConfigureServerModeDebugConnectionParameters() {
    AutoCloseableSoftAssertions().use { softAssertions ->
      val debuggerScriptExecution = PythonScriptExecution()
      val localTargetEnvironment = LocalTargetEnvironment(LocalTargetEnvironmentRequest())
      PyDebugRunner.configureServerModeDebugConnectionParameters(debuggerScriptExecution, constant(8787))
      softAssertions
        .assertThat(debuggerScriptExecution.pythonScriptPath)
        .isNull()
      softAssertions
        .assertThat(debuggerScriptExecution.workingDir)
        .isNull()
      softAssertions
        .assertThat(debuggerScriptExecution.parameters.map { it.apply(localTargetEnvironment) })
        .isEqualTo(listOf("--port", "8787", "--file"))
    }
  }
}