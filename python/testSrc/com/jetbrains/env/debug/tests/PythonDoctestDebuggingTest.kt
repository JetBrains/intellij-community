// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.debug.tests

import com.intellij.execution.RunManager.Companion.getInstance
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.testFramework.UsefulTestCase
import com.jetbrains.env.PyEnvTestCase
import com.jetbrains.env.PyTestTask
import com.jetbrains.env.debug.tasks.PyCustomConfigDebuggerTask
import com.jetbrains.python.debugger.PyDebugRunner
import com.jetbrains.python.run.AbstractPythonRunConfiguration
import com.jetbrains.python.run.CommandLinePatcher
import com.jetbrains.python.run.PythonCommandLineState
import com.jetbrains.python.testing.PythonTestConfigurationType
import com.jetbrains.python.testing.doctest.PythonDocTestRunConfiguration
import org.junit.Assert
import org.junit.Assume
import org.junit.Test

class PythonDoctestDebuggingTest : PyEnvTestCase() {
  companion object {
    private const val VERBOSE_KEY = "-v"
    private const val FAIL_FAST_KEY = "-f"
    private const val DONT_ACCEPT_TRUE_FOR_1 = "-o DONT_ACCEPT_TRUE_FOR_1"
    private const val NORMALIZE_WHITESPACE = "-o NORMALIZE_WHITESPACE"

    private fun concatAllParameters(vararg params: String): String {
      val result = StringBuilder()
      params.forEach {
        result.append("$it ")
      }

      result.deleteCharAt(result.length - 1)
      return result.toString()
    }
  }

  private class DoctestDebuggingTask(scriptName: String = DEFAULT_TEST_FILE, parameters: String? = null) : PyCustomConfigDebuggerTask(RELATIVE_PATH) {
    private val myParameters: String?

    init {
      setScriptName(scriptName)
      myParameters = parameters
    }

    constructor(parameters: String) : this(DEFAULT_TEST_FILE, parameters)

    private fun defaultBefore() {
      addBreakpoint(DEFAULT_BREAKPOINT)
      myParameters?.let { addParameters(it) }
    }

    private fun defaultTesting() {
      waitForPause()
      consoleExec(CONSOLE_EXEC)
      waitForOutput(CONSOLE_EXEC)
      removeBreakpoint(DEFAULT_BREAKPOINT)
      resume()
      waitForTerminate()
      checkPydevWarning()
      assertVerbose()
    }

    fun addBreakpoint(line: Int) {
      toggleBreakpoint(getFilePath(scriptName), line)
    }

    fun removeBreakpoint(line: Int) {
      removeBreakpoint(getFilePath(scriptName), line)
    }

    fun addParameters(params: String) {
      (myRunConfiguration as? PythonDocTestRunConfiguration)?.addParameters(params)
    }

    override fun before() {
      defaultBefore()
    }

    override fun testing() {
      defaultTesting()
    }

    fun assertVerbose() {
      if (myParameters != null && myParameters.contains(VERBOSE_KEY)) {
        Assert.assertTrue(output().contains(RESULT))
      }
    }

    fun checkPydevWarning() {
      Assert.assertFalse(output().contains(PYDEV_WARNING))
    }

    override fun createRunConfiguration(sdkHome: String, existingSdk: Sdk?): AbstractPythonRunConfiguration<*> {
      val factory: ConfigurationFactory = PythonTestConfigurationType.getInstance().docTestFactory
      val runConfiguration = PythonDocTestRunConfiguration(project, factory)
      val runner = ProgramRunner.getRunner(executorId, runConfiguration) as PyDebugRunner?
      val executor = DefaultDebugExecutor.getDebugExecutorInstance()
      runConfiguration.sdkHome = sdkHome
      runConfiguration.sdk = existingSdk
      runConfiguration.module = myFixture.module
      runConfiguration.workingDirectory = myFixture.tempDirPath
      runConfiguration.scriptName = scriptName
      Assert.assertTrue(runner!!.canRun(executor.id, runConfiguration))
      mySettings = getInstance(project).createConfiguration(scriptName + javaClass.name
                                                            + "_RunConfiguration", factory)
      return runConfiguration
    }

    companion object {
      private const val RELATIVE_PATH = "/debug/"
      private const val DEFAULT_TEST_FILE = "test_doctest.py"
      private const val DEFAULT_BREAKPOINT = 45
      private const val PYDEV_WARNING = "PYDEV DEBUGGER WARNING:\nsys.settrace() should not be used when the debugger is being used."
      private val RESULT = """
      Trying:
          [factorial(n) for n in range(6)]
      Expecting:
          [1, 1, 2, 6, 24, 120]
          """.trimIndent()
      private const val CONSOLE_EXEC = "_testing_doctest_"
    }
  }

  override fun runPythonTest(testTask: PyTestTask?) {
    // Don't run on TeamCity because of PY-45432.
    Assume.assumeFalse(UsefulTestCase.IS_UNDER_TEAMCITY)
    super.runPythonTest(testTask)
  }

  @Test
  fun testSimple() {
    runPythonTest(DoctestDebuggingTask())
  }

  @Test
  fun testWithVerbose() {
    runPythonTest(DoctestDebuggingTask(VERBOSE_KEY))
  }

  @Test
  fun testWithFailFast() {
    runPythonTest(DoctestDebuggingTask(FAIL_FAST_KEY))
  }

  @Test
  fun testWithOptionKey() {
    runPythonTest(DoctestDebuggingTask(DONT_ACCEPT_TRUE_FOR_1))
  }

  @Test
  fun testWithOptionKeys() {
    runPythonTest(DoctestDebuggingTask(concatAllParameters(DONT_ACCEPT_TRUE_FOR_1, NORMALIZE_WHITESPACE)))
  }

  @Test
  fun testWithAllParameters() {
    runPythonTest(DoctestDebuggingTask(concatAllParameters(VERBOSE_KEY, FAIL_FAST_KEY, DONT_ACCEPT_TRUE_FOR_1, NORMALIZE_WHITESPACE)))
  }
}