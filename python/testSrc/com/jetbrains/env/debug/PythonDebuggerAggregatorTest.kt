// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.debug

import com.intellij.xdebugger.XDebuggerManager
import com.jetbrains.env.PyEnvTestCase
import org.junit.Test
import org.junit.Assert.assertEquals

class PythonDebuggerAggregatorTest: PyEnvTestCase() {

  @Test
  fun testSeveralReturnSignal() {
    runPythonTest(object: PyDebuggerTask("/debug", "test_several_return_signal.py") {
      override fun before() {
        toggleBreakpoints(6)
      }

      private fun stepIntoFunAndReturn() {
        stepOver()
        waitForPause()
        stepInto()
        waitForPause()
        // check STEP_INTO_CMD
        assertEquals(1, XDebuggerManager.getInstance(project).currentSession?.currentPosition?.line)
        stepOver()
        waitForPause()
      }

      override fun testing() {
        waitForPause()
        stepIntoFunAndReturn()

        val session = XDebuggerManager.getInstance(project).currentSession

        // check PY_RETURN signal
        assertEquals(7, session?.currentPosition?.line)

        resume()
        waitForPause()
        removeBreakpoint(scriptName, 6)
        stepIntoFunAndReturn()

        // check PY_RETURN signal
        assertEquals(7, session?.currentPosition?.line)
        resume()

        waitForTerminate()
      }
    })
  }
}