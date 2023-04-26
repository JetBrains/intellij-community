// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.debug

import com.intellij.openapi.util.SystemInfo
import com.jetbrains.env.PyEnvTestCase
import org.junit.Assume
import org.junit.Test

class PythonDebuggerCythonSpeedupsTest : PyEnvTestCase() {
  @Test
  fun `ensure speedups available`() {
    // Only needs to run on macOS and Windows, as we do not provide pre-built speedups for Linux
    Assume.assumeFalse("No pre-built speedups for Linux", SystemInfo.isLinux)

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
