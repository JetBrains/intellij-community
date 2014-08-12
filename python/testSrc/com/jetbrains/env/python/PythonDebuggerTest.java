package com.jetbrains.env.python;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.xdebugger.XDebuggerTestUtil;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.python.debug.PyDebuggerTask;
import com.jetbrains.env.ut.PyUnitTestTask;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.console.pydev.PydevCompletionVariant;
import com.jetbrains.python.debugger.PyDebuggerException;
import com.jetbrains.python.debugger.PyExceptionBreakpointProperties;
import com.jetbrains.python.debugger.PyExceptionBreakpointType;
import com.jetbrains.python.debugger.pydev.ProcessDebugger;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;

import java.util.List;
import java.util.Set;

/**
 * @author traff
 */

public class PythonDebuggerTest extends PyEnvTestCase {
  public void testBreakpointStopAndEval() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test1.py") {
      @Override
      public void before() throws Exception {
        toggleBreakpoint(getScriptPath(), 3);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();

        eval("i").hasValue("0");

        resume();

        waitForPause();

        eval("i").hasValue("1");

        resume();

        waitForPause();

        eval("i").hasValue("2");
      }
    });
  }

  public void testDebugger() {
    runPythonTest(new PyUnitTestTask("", "test_debug.py") {
      @Override
      protected String getTestDataPath() {
        return PythonHelpersLocator.getPythonCommunityPath() + "/helpers/pydev";
      }

      @Override
      public void after() {
        allTestsPassed();
      }
    });
  }

  public void testConditionalBreakpoint() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test1.py") {
      @Override
      public void before() throws Exception {
        toggleBreakpoint(getScriptPath(), 3);
        XDebuggerTestUtil.setBreakpointCondition(getProject(), 3, "i == 1 or i == 11 or i == 111");
      }

      @Override
      public void testing() throws Exception {
        waitForPause();

        eval("i").hasValue("1");

        resume();

        waitForPause();

        eval("i").hasValue("11");

        resume();

        waitForPause();

        eval("i").hasValue("111");
      }
    });
  }

  public void testDebugConsole() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test1.py") {
      @Override
      public void before() throws Exception {
        toggleBreakpoint(getScriptPath(), 3);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();

        eval("i").hasValue("0");

        resume();

        waitForPause();

        consoleExec("'i=%d'%i");

        waitForOutput("'i=1'");

        consoleExec("x");

        waitForOutput("name 'x' is not defined");

        consoleExec("1-;");

        waitForOutput("SyntaxError");

        resume();
      }

      private void consoleExec(String command) {
        myDebugProcess.consoleExec(command, new ProcessDebugger.DebugCallback<String>() {
          @Override
          public void ok(String value) {

          }

          @Override
          public void error(PyDebuggerException exception) {
          }
        });
      }
    });
  }


  public void testDebugCompletion() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test4.py") {
      @Override
      public void before() throws Exception {
        toggleBreakpoint(getScriptPath(), 3);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();

        List<PydevCompletionVariant> list = myDebugProcess.getCompletions("xvalu");
        assertEquals(2, list.size());
      }
    });
  }

  public void testBreakpointLogExpression() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test1.py") {
      @Override
      public void before() throws Exception {
        toggleBreakpoint(getScriptPath(), 3);
        XDebuggerTestUtil.setBreakpointLogExpression(getProject(), 3, "'i = %d'%i");
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        resume();
        waitForOutput("i = 1");
      }
    });
  }

  public void testStepOver() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test2.py") {
      @Override
      public void before() throws Exception {
        toggleBreakpoint(getScriptPath(), 5);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        stepOver();
        waitForPause();
        stepOver();
        waitForPause();
        eval("z").hasValue("2");
      }
    });
  }

  public void testStepInto() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test2.py") {
      @Override
      public void before() throws Exception {
        toggleBreakpoint(getScriptPath(), 5);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        stepInto();
        waitForPause();
        eval("x").hasValue("1");
        stepOver();
        waitForPause();
        eval("y").hasValue("3");
        stepOver();
        waitForPause();
        eval("z").hasValue("1");
      }
    });
  }

  public void testSmartStepInto() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test3.py") {
      @Override
      public void before() throws Exception {
        toggleBreakpoint(getScriptPath(), 14);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        smartStepInto("foo");
        waitForPause();
        stepOver();
        waitForPause();
        eval("y").hasValue("4");
      }
    });
  }

  public void testSmartStepInto2() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test3.py") {
      @Override
      public void before() throws Exception {
        toggleBreakpoint(getScriptPath(), 18);
        toggleBreakpoint(getScriptPath(), 25);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        toggleBreakpoint(getScriptPath(), 18);
        smartStepInto("foo");
        waitForPause();
        eval("a.z").hasValue("1");
      }
    });
  }

  public void testInput() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test_input.py") {
      @Override
      public void before() throws Exception {
      }

      @Override
      public void testing() throws Exception {
        waitForOutput("print command >");
        input("GO!");
        waitForOutput("command was GO!");
      }

      @Override
      public Set<String> getTags() {
        return ImmutableSet.of("-jython"); //can't run on jython
      }
    });
  }

  public void testRunToLine() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test_runtoline.py") {
      @Override
      public void before() throws Exception {
        toggleBreakpoint(getScriptPath(), 1);
        toggleBreakpoint(getScriptPath(), 7);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        eval("x").hasValue("0");
        runToLine(4);
        eval("x").hasValue("1");
        resume();
        waitForPause();
        eval("x").hasValue("12");
        resume();

        waitForOutput("x = 12");
      }
    });
  }

  private static void addExceptionBreakpoint(IdeaProjectTestFixture fixture, PyExceptionBreakpointProperties properties) {
    XDebuggerTestUtil.addBreakpoint(fixture.getProject(), PyExceptionBreakpointType.class, properties);
  }

  public void testExceptionBreakpointOnTerminate() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test_exceptbreak.py") {
      @Override
      public void before() throws Exception {
        createExceptionBreak(myFixture, true, false, false);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        eval("__exception__[0].__name__").hasValue("'ZeroDivisionError'");
        resume();
        waitForTerminate();
      }

      @Override
      public Set<String> getTags() {
        return ImmutableSet.of("-iron");
      }
    });
  }

  private static void createExceptionBreak(IdeaProjectTestFixture fixture,
                                           boolean notifyOnTerminate,
                                           boolean notifyAlways,
                                           boolean notifyOnFirst) {
    XDebuggerTestUtil.removeAllBreakpoints(fixture.getProject());
    XDebuggerTestUtil.setDefaultBreakpointEnabled(fixture.getProject(), PyExceptionBreakpointType.class, false);

    PyExceptionBreakpointProperties properties = new PyExceptionBreakpointProperties("exceptions.ZeroDivisionError");
    properties.setNotifyOnTerminate(notifyOnTerminate);
    properties.setNotifyAlways(notifyAlways);
    properties.setNotifyOnlyOnFirst(notifyOnFirst);
    addExceptionBreakpoint(fixture, properties);
    properties = new PyExceptionBreakpointProperties("builtins.ZeroDivisionError"); //for python 3
    properties.setNotifyOnTerminate(notifyOnTerminate);
    properties.setNotifyAlways(notifyAlways);
    properties.setNotifyOnlyOnFirst(notifyOnFirst);
    addExceptionBreakpoint(fixture, properties);
  }

  public void testExceptionBreakpointAlways() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test_exceptbreak.py") {
      @Override
      public void before() throws Exception {
        createExceptionBreak(myFixture, false, true, false);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        eval("__exception__[0].__name__").hasValue("'ZeroDivisionError'");
        resume();
        waitForPause();
        resume();
        waitForPause();
        resume();
        waitForTerminate();
      }

      @Override
      public Set<String> getTags() {
        return ImmutableSet.of("-pypy"); //TODO: fix it for Pypy
      }
    });
  }

  public void testExceptionBreakpointOnFirstRaise() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test_exceptbreak.py") {
      @Override
      public void before() throws Exception {
        createExceptionBreak(myFixture, false, false, true);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        eval("__exception__[0].__name__").hasValue("'ZeroDivisionError'");
        resume();
        waitForTerminate();
      }

      @Override
      public Set<String> getTags() {
        return ImmutableSet.of("-iron");
      }
    });
  }

  public void testMultithreading() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test_multithread.py") {
      @Override
      public void before() throws Exception {
        toggleBreakpoint(getScriptPath(), 9);
        toggleBreakpoint(getScriptPath(), 13);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        eval("y").hasValue("2");
        resume();
        waitForPause();
        eval("z").hasValue("102");
        resume();
      }

      @Override
      public Set<String> getTags() {
        return ImmutableSet.of("-pypy"); //TODO: fix that for PyPy
      }
    });
  }

  public void testEggDebug() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test_egg.py") {
      @Override
      public void before() throws Exception {
        String egg = getFilePath("Adder-0.1.egg");
        toggleBreakpointInEgg(egg, "adder/adder.py", 2);
        PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(getRunConfiguration().getSdkHome());
        if (flavor != null) {
          flavor.initPythonPath(Lists.newArrayList(egg), getRunConfiguration().getEnvs());
        }
        else {
          getRunConfiguration().getEnvs().put("PYTHONPATH", egg);
        }
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        eval("ret").hasValue("16");
        resume();
      }

      @Override
      public Set<String> getTags() {
        return ImmutableSet.of("-jython"); //TODO: fix that for Jython if anybody needs it
      }
    });
  }

  public void testStepOverConditionalBreakpoint() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test_stepOverCondition.py") {
      @Override
      public void before() throws Exception {
        toggleBreakpoint(getScriptPath(), 1);
        toggleBreakpoint(getScriptPath(), 2);
        XDebuggerTestUtil.setBreakpointCondition(getProject(), 2, "y == 3");
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        stepOver();
        waitForPause();
        eval("y").hasValue("2");
      }
    });
  }

  public void testMultiprocess() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test_multiprocess.py") {
      @Override
      protected void init() {
        setMultiprocessDebug(true);
      }

      @Override
      public void before() throws Exception {
        toggleBreakpoint(getScriptPath(), 9);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();

        eval("i").hasValue("'Result:OK'");

        resume();

        waitForOutput("Result:OK");
      }

      @Override
      public Set<String> getTags() {
        return Sets.newHashSet("python3");
      }
    });
  }


  //TODO: fix me as I don't work properly sometimes (something connected with process termination on agent)
  //public void testResume() throws Exception {
  //  runPythonTest(new PyDebuggerTask("/debug", "Test_Resume.py") {
  //    @Override
  //    public void before() throws Exception {
  //      toggleBreakpoint(getScriptPath(), 2);
  //    }
  //
  //    @Override
  //    public void testing() throws Exception {
  //      waitForPause();
  //      eval("x").hasValue("1");
  //      resume();
  //      waitForPause();
  //      eval("x").hasValue("2");
  //      resume();
  //    }
  //  });
  //}


  //TODO: first fix strange hanging of that test
  //public void testRemoteDebug() throws Exception {
  //  runPythonTest(new PyRemoteDebuggerTask("/debug", "test_remote.py") {
  //    @Override
  //    public void before() throws Exception {
  //    }
  //
  //    @Override
  //    public void testing() throws Exception {
  //      waitForPause();
  //      eval("x").hasValue("0");
  //      stepOver();
  //      waitForPause();
  //      eval("x").hasValue("1");
  //      stepOver();
  //      waitForPause();
  //      eval("x").hasValue("2");
  //      resume();
  //    }
  //
  //    @Override
  //    protected void checkOutput(ProcessOutput output) {
  //      assertEmpty(output.getStderr());
  //      assertEquals("OK", output.getStdout().trim());
  //    }
  //
  //    @Override
  //    public void after() throws Exception {
  //      stopDebugServer();
  //    }
  //  });
  //}

  //TODO: That doesn't work now: case from test_continuation.py and test_continuation2.py are treated differently by interpreter
  // (first line is executed in first case and last line in second)

  //public void testBreakOnContinuationLine() throws Exception {
  //  runPythonTest(new PyDebuggerTask("/debug", "test_continuation.py") {
  //    @Override
  //    public void before() throws Exception {
  //      toggleBreakpoint(getScriptPath(), 13);
  //    }
  //
  //    @Override
  //    public void testing() throws Exception {
  //      waitForPause();
  //      eval("x").hasValue("0");
  //      stepOver();
  //      waitForPause();
  //      eval("x").hasValue("1");
  //      stepOver();
  //      waitForPause();
  //      eval("x").hasValue("2");
  //    }
  //  });
  //}
}

