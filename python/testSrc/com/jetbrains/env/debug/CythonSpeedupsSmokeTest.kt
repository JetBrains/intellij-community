package com.jetbrains.env.debug

import com.jetbrains.env.PyEnvTestCase
import org.junit.Test

class CythonSpeedupsSmokeTest : PyEnvTestCase() {
  @Test
  fun `ensure speedups available`() {
    runPythonTest(object : PyDebuggerTaskPython3Only("/debug", "test2.py") {
      override fun testing() {
        waitForOutput(USING_CYTHON_SPEEDUPS_MESSAGE)
      }

      // Printing of the debug message happens on early stages when the debugger command line
      // is not parsed yet. Setting the environment variable forces the debug output print.
      override fun getEnvs() = mapOf("PYCHARM_DEBUG" to "True")
    })
  }

  companion object {
    const val USING_CYTHON_SPEEDUPS_MESSAGE = "Using Cython speedups"
  }
}

private open class PyDebuggerTaskPython3Only(relativeTestDataPath: String, scriptName: String)
  : PyDebuggerTask(relativeTestDataPath, scriptName) {
  override fun getTags() = setOf("-python2.7")
}
