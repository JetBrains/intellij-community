// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.doctest

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.value.TargetEnvironmentFunction
import com.intellij.execution.target.value.plus
import com.intellij.execution.target.value.targetPath
import com.jetbrains.python.PythonHelper
import com.jetbrains.python.run.PythonScriptExecution
import com.jetbrains.python.testing.AbstractPythonLegacyTestRunConfiguration.TestType.TEST_CLASS
import com.jetbrains.python.testing.AbstractPythonLegacyTestRunConfiguration.TestType.TEST_FOLDER
import com.jetbrains.python.testing.AbstractPythonLegacyTestRunConfiguration.TestType.TEST_FUNCTION
import com.jetbrains.python.testing.AbstractPythonLegacyTestRunConfiguration.TestType.TEST_METHOD
import com.jetbrains.python.testing.AbstractPythonLegacyTestRunConfiguration.TestType.TEST_SCRIPT
import com.jetbrains.python.testing.PyTestSpecArg
import com.jetbrains.python.testing.PythonTestCommandLineStateBase
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path
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

  override fun addAfterParameters(targetEnvironmentRequest: TargetEnvironmentRequest,
                                  testScriptExecution: PythonScriptExecution) {
    configuration.parametersList.forEach { param -> testScriptExecution.addParameter(param) }
  }

  @TestOnly
  override fun addAfterParameters(cmd: GeneralCommandLine) {
    cmd.parametersList.getParamsGroup(GROUP_SCRIPT)?.addParameters(configuration.parametersList)
  }

  companion object {
    /**
     * *To be deprecated. The part of the legacy implementation based on [GeneralCommandLine].*
     */
    @ApiStatus.Internal
    fun PythonDocTestRunConfiguration.buildTestSpec(): String = buildTestSpecArg().asLocalString()

    /**
     * The same specification as [buildTestSpec], with the path still apart from the runner syntax.
     *
     * The doctest runner reads a test name after `::::` and a folder pattern after `/;`. Only this builder knows
     * where the path ends, so a launcher that has to convert the path for another environment reads this.
     * [PyTestSpecArg] says why.
     */
    @ApiStatus.Internal
    fun PythonDocTestRunConfiguration.buildTestSpecArg(): PyTestSpecArg.WithPath =
      when (testType) {
        TEST_SCRIPT -> PyTestSpecArg.WithPath(scriptName)
        TEST_CLASS -> PyTestSpecArg.WithPath(scriptName, "::$className")
        TEST_METHOD -> PyTestSpecArg.WithPath(scriptName, "::$className::$methodName")
        TEST_FOLDER ->
          if (usePattern() && !pattern.isEmpty()) PyTestSpecArg.WithPath(folderName, "/;$pattern")
          else PyTestSpecArg.WithPath(folderName, "/")
        TEST_FUNCTION -> PyTestSpecArg.WithPath(scriptName, "::::$methodName")
        else -> throw IllegalArgumentException("Unknown test type: $testType")
      }

    private fun PythonDocTestRunConfiguration.buildTestSpec(request: TargetEnvironmentRequest): TargetEnvironmentFunction<String> {
      val spec = buildTestSpecArg()
      return targetPath(Path.of(spec.path)) + spec.suffix
    }
  }
}