// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.debug.tests

import com.jetbrains.env.PyEnvTestCase
import com.jetbrains.env.debug.tasks.PyDebuggerTask
import com.jetbrains.python.debugger.PyDebuggerException
import com.jetbrains.python.debugger.QuotingPolicy
import com.jetbrains.python.debugger.getQuotingString
import com.jetbrains.python.psi.LanguageLevel
import org.junit.Assert
import org.junit.Test

class PythonDebuggerCopyActionTest : PyEnvTestCase() {
  @Test
  fun testGetFullValueFromCopyAction() {
    runPythonTest(object : CopyValueTestCase("test_get_full_value_from_copy_action.py", 10000) {
      override fun before() {
        toggleBreakpoint(getFilePath(scriptName), 5)
      }

      @Throws(Exception::class)
      override fun testing() {
        waitForPause()
        testLength("lst")
        resume()
      }
    })
  }

  @Test
  fun testGetFullValueFromCopyActionClass() {
    runPythonTest(object : CopyValueTestCase("test_get_full_value_from_copy_action_class.py", 400) {
      override fun before() {
        toggleBreakpoint(getFilePath(scriptName), 38)
      }

      @Throws(Exception::class)
      override fun testing() {
        waitForPause()
        testLength("foo")
        resume()
      }
    })
  }


  @Test
  fun testQuotingInCopyAction() {
    runPythonTest(object : PyDebuggerTask("/debug", "test_quoting_value.py") {
      @Throws(PyDebuggerException::class)
      fun testQuotingValue(value: String?) {
        val variable = myDebugProcess.evaluate(value, false, false).value
        for (policy in QuotingPolicy.entries) {
          val result = getQuotingString(policy, variable!!)
          when (policy) {
            QuotingPolicy.DOUBLE -> Assert.assertFalse(result.contains("'"))
            QuotingPolicy.SINGLE -> Assert.assertFalse(result.contains("\""))
            QuotingPolicy.NONE -> {
              Assert.assertFalse(result.contains("'"))
              Assert.assertFalse(result.contains("\""))
            }
          }
        }
      }

      override fun before() {
        toggleBreakpoint(getFilePath(scriptName), 10)
      }

      @Throws(Exception::class)
      override fun testing() {
        waitForPause()
        testQuotingValue("car")
        testQuotingValue("some_str")
        testQuotingValue("some_lst")
        testQuotingValue("some_dict")
        resume()
      }
    })
  }
}

private open class CopyValueTestCase(scriptName: String, private val minLen: Int): PyDebuggerTask("/debug", scriptName) {
  @Throws(PyDebuggerException::class)
  fun testLength(value: String?) {
    // PyXCopyAction uses PyFullValueEvaluator, it uses myDebugProcess.evaluate
    val result = myDebugProcess.evaluate(value, false, false)
    Assert.assertTrue(result.value!!.length > minLen)
  }

  override fun isLanguageLevelSupported(level: LanguageLevel): Boolean {
    return level > LanguageLevel.PYTHON27
  }
}