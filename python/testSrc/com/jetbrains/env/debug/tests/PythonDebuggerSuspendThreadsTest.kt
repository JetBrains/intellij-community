// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.debug.tests

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.xdebugger.breakpoints.SuspendPolicy
import com.jetbrains.env.PyEnvTestCase
import com.jetbrains.env.debug.tasks.PyDebuggerTask
import com.jetbrains.python.debugger.PyThreadInfo
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.run.AbstractPythonRunConfiguration
import com.jetbrains.python.run.PythonRunConfiguration
import org.junit.Assert
import org.junit.Test

class PythonDebuggerSuspendThreadsTest : PyEnvTestCase() {
  @Test
  fun testSuspendAllThreadsPolicy() {
    runPythonTest(object : PyDebuggerTask("/debug", "test_two_threads.py") {

      override fun before() {
        toggleBreakpoint(getFilePath(scriptName), 17)
        setBreakpointSuspendPolicy(project, 17, SuspendPolicy.ALL)
        setWaitForTermination(false)
      }

      @Throws(Exception::class)
      override fun testing() {
        waitForAllThreadsPause()
        eval("m").hasValue("42")
        Assert.assertNull(runningThread)
        resume()
      }

      override fun isLanguageLevelSupported(level: LanguageLevel): Boolean {
        return level > LanguageLevel.PYTHON38
      }
    })
  }

  @Test
  fun testSuspendAllThreadsResume() {
    runPythonTest(object : PyDebuggerTask("/debug", "test_two_threads_resume.py") {
      override fun createRunConfiguration(sdkHome: String, existingSdk: Sdk?): AbstractPythonRunConfiguration<*> {
        val runConfiguration = super.createRunConfiguration(sdkHome, existingSdk) as PythonRunConfiguration
        runConfiguration.envs["PYDEVD_USE_CYTHON"] = "NO"
        return runConfiguration
      }


      override fun before() {
        toggleBreakpoint(getFilePath(scriptName), 10)
        setBreakpointSuspendPolicy(project, 10, SuspendPolicy.ALL)
        setWaitForTermination(false)
      }

      @Throws(Exception::class)
      override fun testing() {
        waitForPause()
        eval("x").hasValue("12")
        resume()
        waitForPause()
        eval("x").hasValue("12")
        resume()
      }

      override fun isLanguageLevelSupported(level: LanguageLevel): Boolean {
        return level > LanguageLevel.PYTHON38
      }
    })
  }

  @Test
  fun testSuspendOneThreadPolicy() {
    runPythonTest(object : PyDebuggerTask("/debug", "test_two_threads.py") {
      override fun before() {
        toggleBreakpoint(getFilePath(scriptName), 17)
        setBreakpointSuspendPolicy(project, 17, SuspendPolicy.THREAD)
        setWaitForTermination(false)
      }

      @Throws(Exception::class)
      override fun testing() {
        waitForPause()
        eval("m").hasValue("42")
        val runningThread = myDebugProcess.threads.stream().filter { thread: PyThreadInfo -> "Thread1" == thread.name }.findFirst()
        Assert.assertNotNull(runningThread)
        setProcessCanTerminate(true)
        disposeDebugProcess()
      }

      override fun isLanguageLevelSupported(level: LanguageLevel): Boolean {
        return level > LanguageLevel.PYTHON38
      }
    })
  }
}