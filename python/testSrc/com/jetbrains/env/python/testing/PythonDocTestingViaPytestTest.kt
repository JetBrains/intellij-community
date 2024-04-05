// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.python.testing

import com.intellij.execution.RunManager
import com.intellij.openapi.project.Project
import com.jetbrains.env.*
import com.jetbrains.env.ut.PyScriptTestProcessRunner
import com.jetbrains.env.ut.PyScriptTestProcessRunner.TEST_TARGET_PREFIX
import com.jetbrains.python.testing.PyTestConfiguration
import com.jetbrains.python.testing.PyTestFactory
import com.jetbrains.python.testing.PythonTestConfigurationType
import com.jetbrains.python.tools.sdkTools.SdkCreationType
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Test

const val DOCTEST_MODULES_ARG = "--doctest-modules"
const val DOCTEST_DIR_PATH = "/testRunner/env/doc/via_pytest"
const val DOCTEST_SIMPLE_MODULE = "doctest_simple"
const val DOCTEST_SIMPLE_FILE = "$DOCTEST_SIMPLE_MODULE.py"
const val DOCTEST_SIMPLE_CLASS = "TestFooClass"
const val DOCTEST_SIMPLE_FUN_TRUE = "test_true"
const val DOCTEST_SIMPLE_FUN_FALSE = "test_false"

internal class PyDocTestViaPytestProducer : PyConfigurationProducerForRunner<PyTestConfiguration> {
  override fun createConfiguration(project: Project, configurationClass: Class<PyTestConfiguration>): PyTestConfiguration {
    val configuration = RunManager.getInstance(project).createConfiguration("test", PyTestFactory(PythonTestConfigurationType.getInstance())).configuration
    assert(configurationClass.isInstance(configuration))
    @Suppress("UNCHECKED_CAST")
    configuration as PyTestConfiguration
    configuration.additionalArguments += DOCTEST_MODULES_ARG
    return configuration
  }
}

class PyDocTestViaPytestRunner(scriptName: String, timesToRerun: Int = 0)
  : PyScriptTestProcessRunner<PyTestConfiguration>(scriptName, PyDocTestViaPytestProducer(), PyTestConfiguration::class.java, timesToRerun)

internal abstract class DocTestViaPytestTask(private val target: String,
                                             private val timesToRerun: Int = 0,
                                             relativePath: String = DOCTEST_DIR_PATH,
                                             sdkCreationType: SdkCreationType = SdkCreationType.EMPTY_SDK)
  : PyProcessWithConsoleTestTask<PyDocTestViaPytestRunner>(relativePath, sdkCreationType) {
  override fun createProcessRunner(): PyDocTestViaPytestRunner = PyDocTestViaPytestRunner(target, timesToRerun)
}

@EnvTestTagsRequired(tags = ["pytest"])
class PythonDocTestingViaPytestTest : PyEnvTestCase() {
  @Test
  fun testConfigurationProducer() {
    runPythonTest(CreateConfigurationByFileTask(PyTestFactory.id, PyTestConfiguration::class.java, "doctest_via_pytest_test.py"))
  }

  @Test
  fun testScript() {
    runPythonTest(object : DocTestViaPytestTask(DOCTEST_SIMPLE_FILE) {
      override fun checkTestResults(runner: PyDocTestViaPytestRunner, stdout: String, stderr: String, all: String, exitCode: Int) {
        assertEquals(3, runner.passedTestsCount)
        assertEquals(2, runner.failedTestsCount)
        val test = runner.findTestByName("${DOCTEST_SIMPLE_MODULE}_${DOCTEST_SIMPLE_CLASS}_$DOCTEST_SIMPLE_FUN_FALSE")
        assertEquals("doctest_simple.py:24 ([doctest] doctest_simple.TestFooClass.test_false)", test.errorMessage?.trim())
      }
    })
  }

  @Test
  fun testCLass() {
    runPythonTest(object : DocTestViaPytestTask("$TEST_TARGET_PREFIX$DOCTEST_SIMPLE_MODULE.$DOCTEST_SIMPLE_CLASS") {
      override fun checkTestResults(runner: PyDocTestViaPytestRunner, stdout: String, stderr: String, all: String, exitCode: Int) {
        Assert.assertTrue(runner.findTestByName("${DOCTEST_SIMPLE_MODULE}_$DOCTEST_SIMPLE_CLASS").isPassed)
      }
    })
  }

  @Test
  fun testFunTrue() {
    runPythonTest(object : DocTestViaPytestTask("$TEST_TARGET_PREFIX$DOCTEST_SIMPLE_MODULE.$DOCTEST_SIMPLE_CLASS.$DOCTEST_SIMPLE_FUN_TRUE") {
      override fun checkTestResults(runner: PyDocTestViaPytestRunner, stdout: String, stderr: String, all: String, exitCode: Int) {
        Assert.assertTrue(runner.findTestByName("${DOCTEST_SIMPLE_MODULE}_${DOCTEST_SIMPLE_CLASS}_$DOCTEST_SIMPLE_FUN_TRUE").isPassed)
      }
    })
  }

  @Test
  fun testFunFalse() {
    runPythonTest(object : DocTestViaPytestTask("$TEST_TARGET_PREFIX$DOCTEST_SIMPLE_MODULE.$DOCTEST_SIMPLE_CLASS.$DOCTEST_SIMPLE_FUN_FALSE") {
      override fun checkTestResults(runner: PyDocTestViaPytestRunner, stdout: String, stderr: String, all: String, exitCode: Int) {
        val test = runner.findTestByName("${DOCTEST_SIMPLE_MODULE}_${DOCTEST_SIMPLE_CLASS}_$DOCTEST_SIMPLE_FUN_FALSE")
        Assert.assertFalse(test.isPassed)
        assertEquals("doctest_simple.py:24 ([doctest] doctest_simple.TestFooClass.test_false)", test.errorMessage?.trim())
      }
    })
  }
}