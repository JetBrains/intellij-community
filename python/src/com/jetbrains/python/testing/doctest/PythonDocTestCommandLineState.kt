// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.doctest

import com.intellij.execution.runners.ExecutionEnvironment
import com.jetbrains.python.PythonHelper
import com.jetbrains.python.testing.AbstractPythonLegacyTestRunConfiguration.TestType.*
import com.jetbrains.python.testing.PythonTestCommandLineStateBase

class PythonDocTestCommandLineState(private val myConfig: PythonDocTestRunConfiguration, env: ExecutionEnvironment)
  : PythonTestCommandLineStateBase<PythonDocTestRunConfiguration?>(myConfig, env) {
  override fun getRunner(): PythonHelper = PythonHelper.DOCSTRING

  override fun getTestSpecs(): List<String> = listOf(myConfig.buildTestSpec())

  companion object {
    private fun PythonDocTestRunConfiguration.buildTestSpec(): String =
      when (testType) {
        TEST_SCRIPT -> scriptName
        TEST_CLASS -> "$scriptName::$className"
        TEST_METHOD -> "$scriptName::$className::$methodName"
        TEST_FOLDER -> if (usePattern() && !pattern.isEmpty()) "$folderName/;$pattern" else "$folderName/"
        TEST_FUNCTION -> "$scriptName::::$methodName"
        else -> throw IllegalArgumentException("Unknown test type: $testType")
      }
  }
}