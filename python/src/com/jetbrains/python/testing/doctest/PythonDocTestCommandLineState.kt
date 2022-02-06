// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.doctest

import com.intellij.execution.runners.ExecutionEnvironment
import com.jetbrains.python.PythonHelper
import com.jetbrains.python.testing.AbstractPythonLegacyTestRunConfiguration.TestType.*
import com.jetbrains.python.testing.PythonTestCommandLineStateBase

class PythonDocTestCommandLineState(private val myConfig: PythonDocTestRunConfiguration, env: ExecutionEnvironment)
  : PythonTestCommandLineStateBase<PythonDocTestRunConfiguration?>(myConfig, env) {
  override fun getRunner(): PythonHelper {
    return PythonHelper.DOCSTRING
  }

  override fun getTestSpecs(): List<String> {
    val specs: MutableList<String> = ArrayList()
    when (myConfig.testType) {
      TEST_SCRIPT -> specs.add(myConfig.scriptName)
      TEST_CLASS -> specs.add(myConfig.scriptName + "::" + myConfig.className)
      TEST_METHOD -> specs.add(myConfig.scriptName + "::" + myConfig.className + "::" + myConfig.methodName)
      TEST_FOLDER ->
        if (myConfig.usePattern() && !myConfig.pattern.isEmpty()) specs.add(myConfig.folderName + "/" + ";" + myConfig.pattern)
        else specs.add(myConfig.folderName + "/")
      TEST_FUNCTION -> specs.add(myConfig.scriptName + "::::" + myConfig.methodName)
      else -> throw IllegalArgumentException("Unknown test type: " + myConfig.testType)
    }
    return specs
  }
}