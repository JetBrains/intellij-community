// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.debug.smokeTests

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.assertions.Assertions
import com.jetbrains.env.PyEnvTestCase
import com.jetbrains.env.debug.PyDebuggerTask
import com.jetbrains.python.debugger.PyDebugRunner
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class PythonDebuggerRunsWithoutErrorsTest : PyEnvTestCase() {
  private val applicationRule = ApplicationRule()

  private val disableDebuggerTracingRule = DisableDebuggerTracingRule()

  @get:Rule
  val ruleChain = RuleChain(applicationRule, disableDebuggerTracingRule)

  @Test
  fun `ensure no error messages in output`() {
    runPythonTest(object : PyDebuggerTask("debug", "test2.py") {
      init {
        setProcessCanTerminate(true)
      }

      override fun before() {
        // It's essential to stop at a breakpoint. Otherwise, the debugger process
        // can finish before it is fully initialized. `KeyboardInterrupt` may be raised
        // and not caught in such a case.
        toggleBreakpoint(6)
      }

      override fun testing() {
        waitForPause()
        resume()
        if (stderr().isNotEmpty()) {
          Assertions.fail<Unit>("The debugger output contains error messages.")
        }
      }
    })
  }
}

private class DisableDebuggerTracingRule : TestRule {
  override fun apply(base: Statement, description: Description): Statement {
    return object : Statement() {
      override fun evaluate() {
        val original = ApplicationManager.getApplication().getUserData(KEY)
        try {
          ApplicationManager.getApplication().putUserData(KEY, true)
          base.evaluate()
        }
        finally {
          ApplicationManager.getApplication().putUserData(KEY, original)
        }
      }
    }
  }

  companion object {
    private val KEY = PyDebugRunner.FORCE_DISABLE_DEBUGGER_TRACING
  }
}