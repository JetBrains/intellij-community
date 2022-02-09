// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.doctest

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.value.*
import com.jetbrains.python.PythonHelper
import com.jetbrains.python.testing.AbstractPythonLegacyTestRunConfiguration.TestType.*
import com.jetbrains.python.testing.PythonTestCommandLineStateBase
import java.util.function.Function

class PythonDocTestCommandLineState(config: PythonDocTestRunConfiguration, env: ExecutionEnvironment)
  : PythonTestCommandLineStateBase<PythonDocTestRunConfiguration>(config, env) {
  override fun getRunner(): PythonHelper = PythonHelper.DOCSTRING

  /**
   * *To be deprecated. The part of the legacy implementation based on [GeneralCommandLine].*
   */
  override fun getTestSpecs(): List<String> = listOf(configuration.buildTestSpec())

  override fun getTestSpecs(request: TargetEnvironmentRequest): List<Function<TargetEnvironment, String>> =
    listOf(configuration.buildTestSpec(request))

  companion object {
    /**
     * *To be deprecated. The part of the legacy implementation based on [GeneralCommandLine].*
     */
    private fun PythonDocTestRunConfiguration.buildTestSpec(): String =
      when (testType) {
        TEST_SCRIPT -> scriptName
        TEST_CLASS -> "$scriptName::$className"
        TEST_METHOD -> "$scriptName::$className::$methodName"
        TEST_FOLDER -> if (usePattern() && !pattern.isEmpty()) "$folderName/;$pattern" else "$folderName/"
        TEST_FUNCTION -> "$scriptName::::$methodName"
        else -> throw IllegalArgumentException("Unknown test type: $testType")
      }

    private fun PythonDocTestRunConfiguration.buildTestSpec(request: TargetEnvironmentRequest): TargetEnvironmentFunction<String> =
      when (testType) {
        TEST_SCRIPT -> request.getTargetEnvironmentValueForLocalPath(scriptName)
        TEST_CLASS -> request.getTargetEnvironmentValueForLocalPath(scriptName) + "::$className"
        TEST_METHOD -> request.getTargetEnvironmentValueForLocalPath(scriptName) + "::$className::$methodName"
        TEST_FOLDER ->
          if (usePattern() && !pattern.isEmpty()) request.getTargetEnvironmentValueForLocalPath(folderName) + "/;$pattern"
          else request.getTargetEnvironmentValueForLocalPath(folderName) + "/"
        TEST_FUNCTION -> request.getTargetEnvironmentValueForLocalPath(scriptName) + "::::$methodName"
        else -> throw IllegalArgumentException("Unknown test type: $testType")
      }
  }
}