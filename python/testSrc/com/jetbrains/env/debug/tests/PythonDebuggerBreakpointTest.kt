// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.debug.tests

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.xdebugger.XDebuggerTestUtil
import com.jetbrains.env.PyEnvTestCase
import com.jetbrains.env.debug.tasks.PyBaseDebuggerTask
import com.jetbrains.env.debug.tasks.PyDebuggerTask
import com.jetbrains.python.debugger.PyExceptionBreakpointProperties
import com.jetbrains.python.debugger.PyExceptionBreakpointType
import com.jetbrains.python.run.AbstractPythonRunConfiguration
import com.jetbrains.python.run.PythonRunConfiguration
import org.junit.Test

class PythonDebuggerBreakpointTest : PyEnvTestCase() {
  private open class BreakpointStopAndEvalTask(scriptName: String?) : PyDebuggerTask("/debug", scriptName) {
    override fun before() {
      toggleBreakpoint(getFilePath(scriptName), 3)
      setWaitForTermination(false)
    }

    @Throws(Exception::class)
    override fun testing() {
      waitForPause()

      eval("i").hasValue("0")

      resume()

      waitForPause()

      eval("i").hasValue("1")

      resume()

      waitForPause()

      eval("i").hasValue("2")
    }
  }

  private fun createExceptionBreakZeroDivisionError(fixture: IdeaProjectTestFixture) {
    XDebuggerTestUtil.removeAllBreakpoints(fixture.project)
    XDebuggerTestUtil.setDefaultBreakpointEnabled(fixture.project, PyExceptionBreakpointType::class.java, false)

    var properties = PyExceptionBreakpointProperties("exceptions.ZeroDivisionError")
    properties.isNotifyOnTerminate = true
    properties.isNotifyOnlyOnFirst = false
    properties.isIgnoreLibraries = false
    PyBaseDebuggerTask.addExceptionBreakpoint(fixture, properties)
    properties = PyExceptionBreakpointProperties("builtins.ZeroDivisionError") //for python 3
    properties.isNotifyOnTerminate = true
    properties.isNotifyOnlyOnFirst = false
    properties.isIgnoreLibraries = false
    PyBaseDebuggerTask.addExceptionBreakpoint(fixture, properties)
  }

  @Test
  fun testBreakpointStopAndEval() {
    runPythonTest(object : BreakpointStopAndEvalTask("test1.py") {
      override fun before() {
        toggleBreakpoint(getFilePath(scriptName), 6)
        setWaitForTermination(false)
      }
    })
  }

  @Test
  fun testConditionalBreakpoint() {
    runPythonTest(object : PyDebuggerTask("/debug", "test1.py") {
      override fun before() {
        toggleBreakpoint(getFilePath(scriptName), 7)
        XDebuggerTestUtil.setBreakpointCondition(project, 7, "i == 1 or i == 11 or i == 111")
        setWaitForTermination(false)
      }

      @Throws(java.lang.Exception::class)
      override fun testing() {
        waitForPause()
        eval("i").hasValue("1")
        resume()
        waitForPause()
        eval("i").hasValue("11")
        resume()
        waitForPause()
        eval("i").hasValue("111")
      }
    })
  }

  @Test
  fun testBreakpointLogExpression() {
    runPythonTest(object : PyDebuggerTask("/debug", "test1.py") {
      override fun before() {
        toggleBreakpoint(getFilePath(scriptName), 7)
        XDebuggerTestUtil.setBreakpointLogExpression(project, 7, "'i = %d'%i")
        setWaitForTermination(false)
      }

      @Throws(java.lang.Exception::class)
      override fun testing() {
        waitForPause()
        resume()
        waitForOutput("i = 1")
      }
    })
  }

  @Test
  fun testAddBreakWhileRunning() {
    runPythonTest(object : PyDebuggerTask("/debug", "test_resume_after_step.py") {
      override fun before() {
        toggleBreakpoint(scriptName, 2)
        toggleBreakpoint(scriptName, 3)
      }

      @Throws(java.lang.Exception::class)
      override fun testing() {
        waitForPause()
        eval("a").hasValue("1")
        toggleBreakpoint(scriptName, 5)
        resume()
        waitForPause()
        eval("b").hasValue("2")
        resume()
        waitForPause()
        eval("d").hasValue("4")
        resume()
      }
    })
  }

  @Test
  fun testAddBreakAfterRemove() {
    runPythonTest(object : PyDebuggerTask("/debug", "test1.py") {
      override fun before() {
        toggleBreakpoint(scriptName, 6)
      }

      @Throws(java.lang.Exception::class)
      override fun testing() {
        waitForPause()
        eval("i").hasValue("0")
        // remove break on line 2
        removeBreakpoint(scriptName, 6)
        resume()
        // add break on line 2
        toggleBreakpoint(scriptName, 6)
        // check if break on line 2 works
        waitForPause()
        // remove break on line 2 again
        removeBreakpoint(scriptName, 6)
        // add break on line 3
        toggleBreakpoint(scriptName, 7)
        resume()
        // check if break on line 3 works
        waitForPause()
        resume()
      }
    })
  }

  @Test
  fun testAddBreakpointAfterRun() {
    runPythonTest(object: PyDebuggerTask("/debug", "test_add_breakpoint_after_run.py") {
      // TODO: remove after Cython regen
      override fun createRunConfiguration(sdkHome: String, existingSdk: Sdk?): AbstractPythonRunConfiguration<*> {
        val runConfiguration = super.createRunConfiguration(sdkHome, existingSdk) as PythonRunConfiguration
        runConfiguration.envs["PYDEVD_USE_CYTHON"] = "NO"
        return runConfiguration
      }

      override fun before() {
        setWaitForTermination(false)
        toggleBreakpoints(20)
      }

      override fun testing() {
        waitForPause()
        removeBreakpoint(scriptName, 20)
        resume()
        toggleBreakpoints(20)
        waitForPause()
      }
    })
  }

  @Test
  fun testExceptionBreakpointOnTerminate() {
    runPythonTest(object : PyDebuggerTask("/debug", "test_exceptbreak.py") {
      override fun before() {
        createExceptionBreakZeroDivisionError(myFixture)
      }

      @Throws(java.lang.Exception::class)
      override fun testing() {
        waitForPause()
        waitForOutput("message=\"python-exceptions.ZeroDivisionError\"", "message=\"python-builtins.ZeroDivisionError\"")
        waitForOutput("Traceback (most recent call last):")
        waitForOutput("line 7, in foo")
        waitForOutput("return 1 / x")
        waitForOutput("ZeroDivisionError: ")
        eval("__exception__[0].__name__").hasValue("'ZeroDivisionError'")
        resume()
        waitForTerminate()
      }
    })
  }

  @Test
  fun testExceptionBreakpointOnFirstRaise() {
    runPythonTest(object : PyDebuggerTask("/debug", "test_exceptbreak.py") {
      override fun before() {
        createExceptionBreak(myFixture, false, true, true)
      }

      @Throws(java.lang.Exception::class)
      override fun testing() {
        waitForPause()
        waitForOutput("message=\"python-BaseException\"")
        eval("__exception__[0].__name__").hasValue("'IndexError'")
        resume()
      }
    })
  }

  @Test
  fun testExceptionBreakpointIgnoreLibrariesOnRaise() {
    runPythonTest(object : PyDebuggerTask("/debug", "test_ignore_lib.py") {
      override fun before() {
        createExceptionBreak(myFixture, false, true, true)
      }

      @Throws(java.lang.Exception::class)
      override fun testing() {
        waitForPause()
        waitForOutput("message=\"python-BaseException\"")
        eval("stopped_in_user_file").hasValue("True")
        resume()
        waitForTerminate()
      }
    })
  }

  @Test
  fun testExceptionBreakpointIgnoreInUnittestModule() {
    runPythonTest(object : PyDebuggerTask("/debug", "test_ignore_exceptions_in_unittest.py") {
      override fun before() {
        createExceptionBreak(myFixture, false, true, true)
        toggleBreakpoint(getFilePath(scriptName), 2)
      }

      @Throws(java.lang.Exception::class)
      override fun testing() {
        waitForPause()
        resume()
        waitForTerminate()
      }
    })
  }

  @Test
  fun testExceptionBreakpointIgnoreLibrariesOnTerminate() {
    runPythonTest(object : PyDebuggerTask("/debug", "test_ignore_lib.py") {
      override fun before() {
        createExceptionBreak(myFixture, true, false, true)
      }

      @Throws(java.lang.Exception::class)
      override fun testing() {
        waitForPause()
        waitForOutput("message=\"python-BaseException\"")
        eval("stopped_in_user_file").hasValue("True")
        resume()
        waitForTerminate()
      }
    })
  }

  @Test
  fun testExceptionBreakpointConditionOnRaise() {
    runPythonTest(object : PyDebuggerTask("/debug", "test_exceptbreak.py") {
      override fun before() {
        createExceptionBreak(myFixture, false, true, true, "__exception__[0] == ZeroDivisionError", null)
      }

      @Throws(java.lang.Exception::class)
      override fun testing() {
        waitForPause()
        waitForOutput("message=\"python-BaseException\"")
        eval("__exception__[0].__name__").hasValue("'ZeroDivisionError'")
        resume()
        waitForTerminate()
      }
    })
  }

  @Test
  fun testExceptionBreakpointConditionOnTerminate() {
    runPythonTest(object : PyDebuggerTask("/debug", "test_exceptbreak.py") {
      override fun before() {
        createExceptionBreak(myFixture, true, false, false, "__exception__[0] == ZeroDivisionError", null)
      }

      @Throws(java.lang.Exception::class)
      override fun testing() {
        waitForPause()
        waitForOutput("message=\"python-BaseException\"")
        eval("__exception__[0].__name__").hasValue("'ZeroDivisionError'")
        resume()
        waitForTerminate()
      }
    })
  }
}