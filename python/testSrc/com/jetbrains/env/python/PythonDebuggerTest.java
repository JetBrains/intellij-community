package com.jetbrains.env.python;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.xdebugger.XDebuggerTestUtil;
import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.jetbrains.TestEnv;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.PyProcessWithConsoleTestTask;
import com.jetbrains.env.Staging;
import com.jetbrains.env.StagingOn;
import com.jetbrains.env.python.debug.PyDebuggerTask;
import com.jetbrains.env.ut.PyUnitTestProcessRunner;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.console.pydev.PydevCompletionVariant;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyDebuggerException;
import com.jetbrains.python.debugger.PyExceptionBreakpointProperties;
import com.jetbrains.python.debugger.PyExceptionBreakpointType;
import com.jetbrains.python.debugger.pydev.PyDebugCallback;
import com.jetbrains.python.debugger.settings.PyDebuggerSettings;
import com.jetbrains.python.debugger.settings.PySteppingFilter;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import com.jetbrains.python.sdkTools.SdkCreationType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author traff
 */

public class PythonDebuggerTest extends PyEnvTestCase {
  private class BreakpointStopAndEvalTask extends PyDebuggerTask {
    public BreakpointStopAndEvalTask(String scriptName) {
      super("/debug", scriptName);
    }

    @Override
    public void before() throws Exception {
      toggleBreakpoint(getFilePath(getScriptName()), 3);
      setWaitForTermination(false);
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
  }

  @Test
  public void testBreakpointStopAndEval() throws Exception {
    runPythonTest(new BreakpointStopAndEvalTask("test1.py"));
  }

  @Test
  @Staging
  public void testPydevTests_Debugger() {
    unittests("tests_pydevd_python/test_debugger.py", null);
  }

  @Test
  public void testPydevMonkey() {
    unittests("tests_pydevd_python/test_pydev_monkey.py", null);
  }

  @Test
  public void testBytecodeModification() {
    unittests("tests_pydevd_python/test_bytecode_modification.py", Sets.newHashSet("python36"));
  }

  @Test
  @Staging
  public void testFrameEvalAndTracing() {
    unittests("tests_pydevd_python/test_frame_eval_and_tracing.py", Sets.newHashSet("python36"), true);
  }

  private void unittests(final String script, @Nullable Set<String> tags) {
    unittests(script, tags, false);
  }

  private void unittests(final String script, @Nullable Set<String> tags, boolean isSkipAllowed) {
    runPythonTest(new PyProcessWithConsoleTestTask<PyUnitTestProcessRunner>("/helpers/pydev", SdkCreationType.SDK_PACKAGES_ONLY) {

      @NotNull
      @Override
      protected PyUnitTestProcessRunner createProcessRunner() throws Exception {
        return new PyUnitTestProcessRunner(script, 0);
      }

      @NotNull
      @Override
      public String getTestDataPath() {
        return PythonHelpersLocator.getPythonCommunityPath();
      }

      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        if (isSkipAllowed) {
          runner.assertNoFailures();
        }
        else {
          runner.assertAllTestsPassed();
        }
      }

      @NotNull
      @Override
      public Set<String> getTags() {
        if (tags == null) {
          return super.getTags();
        }
        return tags;
      }
    });
  }

  @Test
  public void testConditionalBreakpoint() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test1.py") {
      @Override
      public void before() throws Exception {
        toggleBreakpoint(getFilePath(getScriptName()), 3);
        XDebuggerTestUtil.setBreakpointCondition(getProject(), 3, "i == 1 or i == 11 or i == 111");
        setWaitForTermination(false);
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

  @Test
  public void testDebugConsole() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test1.py") {
      @Override
      public void before() throws Exception {
        toggleBreakpoint(getFilePath(getScriptName()), 3);
        setWaitForTermination(false);
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
        myDebugProcess.consoleExec(command, new PyDebugCallback<String>() {
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

  @Test
  public void testDebugCompletion() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test4.py") {
      @Override
      public void before() throws Exception {
        toggleBreakpoint(getScriptName(), 3);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();

        List<PydevCompletionVariant> list = myDebugProcess.getCompletions("xvalu");
        assertEquals(2, list.size());
      }
    });
  }

  @Test
  public void testBreakpointLogExpression() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test1.py") {
      @Override
      public void before() throws Exception {
        toggleBreakpoint(getFilePath(getScriptName()), 3);
        XDebuggerTestUtil.setBreakpointLogExpression(getProject(), 3, "'i = %d'%i");
        setWaitForTermination(false);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        resume();
        waitForOutput("i = 1");
      }
    });
  }

  @Test
  public void testStepOver() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test2.py") {
      @Override
      public void before() throws Exception {
        toggleBreakpoint(getFilePath(getScriptName()), 5);
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

  @Test
  public void testStepInto() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test2.py") {
      @Override
      public void before() throws Exception {
        toggleBreakpoint(getFilePath(getScriptName()), 5);
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

  @Test
  public void testStepIntoMyCode() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test_my_code.py") {

      @Override
      public void before() throws Exception {
        toggleBreakpoint(getFilePath(getScriptName()), 5);
        toggleBreakpoint(getFilePath(getScriptName()), 7);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        stepIntoMyCode();
        waitForPause();
        eval("x").hasValue("2");
        resume();
        waitForPause();
        eval("x").hasValue("3");
        stepIntoMyCode();
        waitForPause();
        eval("stopped_in_user_file").hasValue("True");
      }
    });
  }

  @Test
  public void testSmartStepInto() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test3.py") {
      @Override
      public void before() throws Exception {
        toggleBreakpoint(getFilePath(getScriptName()), 14);
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

  @Test
  public void testSmartStepInto2() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test3.py") {
      @Override
      public void before() throws Exception {
        toggleBreakpoint(getFilePath(getScriptName()), 18);
        toggleBreakpoint(getFilePath(getScriptName()), 25);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        toggleBreakpoint(getFilePath(getScriptName()), 18);
        smartStepInto("foo");
        waitForPause();
        eval("a.z").hasValue("1");
      }
    });
  }

  @Test
  public void testInput() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test_input.py") {
      @Override
      public void before() throws Exception {
        setWaitForTermination(false);
      }

      @Override
      public void testing() throws Exception {
        waitForOutput("print command >");
        input("GO!");
        waitForOutput("command was GO!");
      }

      @NotNull
      @Override
      public Set<String> getTags() {
        return ImmutableSet.of("-jython"); //can't run on jython
      }
    });
  }

  @Test
  public void testRunToLine() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test_runtoline.py") {
      @Override
      public void before() throws Exception {
        toggleBreakpoint(getFilePath(getScriptName()), 7);
        toggleBreakpoint(getFilePath(getScriptName()), 14);
        setWaitForTermination(false);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        eval("x").hasValue("0");
        runToLine(11);
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

  @Test
  public void testExceptionBreakpointOnTerminate() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test_exceptbreak.py") {
      @Override
      public void before() throws Exception {
        createExceptionBreakZeroDivisionError(myFixture, true, false, false);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        eval("__exception__[0].__name__").hasValue("'ZeroDivisionError'");
        resume();
        waitForTerminate();
      }

      @NotNull
      @Override
      public Set<String> getTags() {
        return ImmutableSet.of("-iron");
      }
    });
  }

  private static void createExceptionBreakZeroDivisionError(IdeaProjectTestFixture fixture,
                                                            boolean notifyOnTerminate,
                                                            boolean notifyOnFirst,
                                                            boolean ignoreLibraries) {
    XDebuggerTestUtil.removeAllBreakpoints(fixture.getProject());
    XDebuggerTestUtil.setDefaultBreakpointEnabled(fixture.getProject(), PyExceptionBreakpointType.class, false);

    PyExceptionBreakpointProperties properties = new PyExceptionBreakpointProperties("exceptions.ZeroDivisionError");
    properties.setNotifyOnTerminate(notifyOnTerminate);
    properties.setNotifyOnlyOnFirst(notifyOnFirst);
    properties.setIgnoreLibraries(ignoreLibraries);
    addExceptionBreakpoint(fixture, properties);
    properties = new PyExceptionBreakpointProperties("builtins.ZeroDivisionError"); //for python 3
    properties.setNotifyOnTerminate(notifyOnTerminate);
    properties.setNotifyOnlyOnFirst(notifyOnFirst);
    properties.setIgnoreLibraries(ignoreLibraries);
    addExceptionBreakpoint(fixture, properties);
  }

  @Test
  public void testExceptionBreakpointOnFirstRaise() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test_exceptbreak.py") {
      @Override
      public void before() throws Exception {
        createExceptionBreakZeroDivisionError(myFixture, false, true, false);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        eval("__exception__[0].__name__").hasValue("'ZeroDivisionError'");
        resume();
        waitForTerminate();
      }

      @NotNull
      @Override
      public Set<String> getTags() {
        return ImmutableSet.of("-iron");
      }
    });
  }

  public static void createExceptionBreak(IdeaProjectTestFixture fixture,
                                          boolean notifyOnTerminate,
                                          boolean notifyOnFirst,
                                          boolean ignoreLibraries) {
    XDebuggerTestUtil.removeAllBreakpoints(fixture.getProject());
    XDebuggerTestUtil.setDefaultBreakpointEnabled(fixture.getProject(), PyExceptionBreakpointType.class, false);

    PyExceptionBreakpointProperties properties = new PyExceptionBreakpointProperties("BaseException");
    properties.setNotifyOnTerminate(notifyOnTerminate);
    properties.setNotifyOnlyOnFirst(notifyOnFirst);
    properties.setIgnoreLibraries(ignoreLibraries);
    addExceptionBreakpoint(fixture, properties);
  }

  @Test
  public void testExceptionBreakpointIgnoreLibrariesOnRaise() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test_ignore_lib.py") {

      @Override
      public void before() throws Exception {
        createExceptionBreak(myFixture, false, true, true);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        eval("stopped_in_user_file").hasValue("True");
        resume();
        waitForTerminate();
      }

      @NotNull
      @Override
      public Set<String> getTags() {
        return ImmutableSet.of("-jython");
      }
    });
  }

  @Test
  public void testExceptionBreakpointIgnoreLibrariesOnTerminate() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test_ignore_lib.py") {

      @Override
      public void before() throws Exception {
        createExceptionBreak(myFixture, true, false, true);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        eval("stopped_in_user_file").hasValue("True");
        resume();
        waitForTerminate();
      }

      @NotNull
      @Override
      public Set<String> getTags() {
        return ImmutableSet.of("-iron");
      }
    });
  }

  @Test
  public void testMultithreading() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test_multithread.py") {
      @Override
      public void before() throws Exception {
        toggleBreakpoint(getFilePath(getScriptName()), 10);
        toggleBreakpoint(getFilePath(getScriptName()), 16);
        setWaitForTermination(false);
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

      @NotNull
      @Override
      public Set<String> getTags() {
        return ImmutableSet.of("-pypy"); //TODO: fix that for PyPy
      }
    });
  }

  @Test
  @StagingOn(os = TestEnv.WINDOWS)
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

      @NotNull
      @Override
      public Set<String> getTags() {
        return ImmutableSet.of("-jython"); //TODO: fix that for Jython if anybody needs it
      }
    });
  }


  @Test
  public void testWinEggDebug() throws Exception {
    if (UsefulTestCase.IS_UNDER_TEAMCITY && !SystemInfo.isWindows) {
      return; // Only needs to run on windows
    }
    runPythonTest(new PyDebuggerTask("/debug", "test_winegg.py") {
      @Override
      public void before() throws Exception {
        String egg = getFilePath("wintestegg-0.1.egg");
        toggleBreakpointInEgg(egg, "eggxample/lower_case.py", 2);
        toggleBreakpointInEgg(egg, "eggxample/MIXED_case.py", 2);

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

        waitForPause();
        eval("ret").hasValue("17");
        resume();
      }

      @NotNull
      @Override
      public Set<String> getTags() {
        return ImmutableSet.of("-jython"); //TODO: fix that for Jython if anybody needs it
      }
    });
  }

  @Test
  public void testWinLongName() throws Exception {
    if (!SystemInfo.isWindows) {
      return; // Only needs to run on windows
    }
    runPythonTest(new PyDebuggerTask("/debug", "long_n~1.py") {
      @Override
      public void before() throws Exception {
        String longName = "long_name_win_test.py";
        toggleBreakpoint(longName, 2);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        eval("x").hasValue("10");
        resume();
      }

      @NotNull
      @Override
      public Set<String> getTags() {
        return ImmutableSet.of("-jython"); //TODO: fix that for Jython if anybody needs it
      }
    });
  }


  @Test
  public void testStepOverConditionalBreakpoint() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test_stepOverCondition.py") {
      @Override
      public void before() throws Exception {
        toggleBreakpoint(getScriptName(), 1);
        toggleBreakpoint(getScriptName(), 2);
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

  @Test
  public void testMultiprocess() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test_multiprocess.py") {
      @Override
      protected void init() {
        setMultiprocessDebug(true);
      }

      @Override
      public void before() throws Exception {
        toggleBreakpoint(getScriptName(), 9);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();

        eval("i").hasValue("'Result:OK'");

        resume();

        waitForOutput("Result:OK");
      }

      @NotNull
      @Override
      public Set<String> getTags() {
        return Sets.newHashSet("python3");
      }
    });
  }

  @Test
  public void testMultiprocessingSubprocess() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test_multiprocess_args.py") {
      @Override
      protected void init() {
        setMultiprocessDebug(true);
      }

      @Override
      public void before() throws Exception {
        toggleBreakpoint(getFilePath("test_remote.py"), 2);
        setWaitForTermination(false);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        eval("sys.argv[1]").hasValue("'subprocess'");
        eval("sys.argv[2]").hasValue("'etc etc'");

        resume();
      }

      @NotNull
      @Override
      public Set<String> getTags() {
        return ImmutableSet.of("-iron", "-jython"); //can't run on iron and jython
      }
    });
  }

  @Test
  public void testPyQtQThreadInheritor() throws Exception {
    if (UsefulTestCase.IS_UNDER_TEAMCITY && SystemInfo.isWindows) {
      return; //Don't run under Windows
    }

    runPythonTest(new PyDebuggerTask("/debug", "test_pyqt1.py") {
      @Override
      protected void init() {
        setMultiprocessDebug(true);
      }

      @Override
      public void before() throws Exception {
        toggleBreakpoint(getScriptName(), 8);
      }

      @Override
      public void testing() throws Exception {

        waitForPause();

        eval("i").hasValue("0");

        resume();

        waitForPause();

        eval("i").hasValue("1");

        resume();
      }

      @NotNull
      @Override
      public Set<String> getTags() {
        return Sets.newHashSet("pyqt5");
      }
    });
  }

  @Test
  public void testPyQtMoveToThread() throws Exception {
    if (UsefulTestCase.IS_UNDER_TEAMCITY && SystemInfo.isWindows) {
      return; //Don't run under Windows
    }

    runPythonTest(new PyDebuggerTask("/debug", "test_pyqt2.py") {
      @Override
      protected void init() {
        setMultiprocessDebug(true);
      }

      @Override
      public void before() throws Exception {
        toggleBreakpoint(getScriptName(), 10);
      }

      @Override
      public void testing() throws Exception {

        waitForPause();

        eval("i").hasValue("0");

        resume();

        waitForPause();

        eval("i").hasValue("1");

        resume();
      }

      @NotNull
      @Override
      public Set<String> getTags() {
        return Sets.newHashSet("pyqt5");
      }
    });
  }


  @Test
  public void testPyQtQRunnableInheritor() throws Exception {
    if (UsefulTestCase.IS_UNDER_TEAMCITY && SystemInfo.isWindows) {
      return; //Don't run under Windows
    }

    runPythonTest(new PyDebuggerTask("/debug", "test_pyqt3.py") {
      @Override
      protected void init() {
        setMultiprocessDebug(true);
      }

      @Override
      public void before() throws Exception {
        toggleBreakpoint(getScriptName(), 9);
      }

      @Override
      public void testing() throws Exception {

        waitForPause();

        eval("i").hasValue("0");

        resume();

        waitForPause();

        eval("i").hasValue("1");

        resume();
      }

      @NotNull
      @Override
      public Set<String> getTags() {
        return Sets.newHashSet("pyqt5");
      }
    });
  }


  @Test
  public void testStepOverYieldFrom() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test_step_over_yield.py") {
      @Override
      protected void init() {
        setMultiprocessDebug(true);
      }

      @Override
      public void before() throws Exception {
        toggleBreakpoint(getScriptName(), 6);
      }

      @Override
      public void testing() throws Exception {

        waitForPause();

        stepOver();

        waitForPause();

        eval("a").hasValue("42");

        stepOver();

        waitForPause();

        eval("a").hasValue("42");

        stepOver();

        waitForPause();

        eval("sum").hasValue("6");

        resume();
      }

      @NotNull
      @Override
      public Set<String> getTags() {
        return Sets.newHashSet("python34");
      }
    });
  }

  @Test
  public void testSteppingFilter() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test_stepping_filter.py") {

      @Override
      public void before() throws Exception {
        toggleBreakpoint(getScriptName(), 4);
        List<PySteppingFilter> filters = new ArrayList<>();
        filters.add(new PySteppingFilter(true, "*/test_m?_code.py"));
        final PyDebuggerSettings debuggerSettings = PyDebuggerSettings.getInstance();
        debuggerSettings.setLibrariesFilterEnabled(true);
        debuggerSettings.setSteppingFiltersEnabled(true);
        debuggerSettings.setSteppingFilters(filters);
      }

      @Override
      public void doFinally() {
        final PyDebuggerSettings debuggerSettings = PyDebuggerSettings.getInstance();
        debuggerSettings.setLibrariesFilterEnabled(false);
        debuggerSettings.setSteppingFiltersEnabled(false);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        stepInto();
        waitForPause();
        eval("stopped_in_user_file").hasValue("True");
        stepInto();
        waitForPause();
        eval("stopped_in_user_file").hasValue("True");
      }
    });
  }

  @Test
  public void testReturnValues() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test_return_values.py") {
      @Override
      public void before() throws Exception {
        toggleBreakpoint(getScriptName(), 2);
        final PyDebuggerSettings debuggerSettings = PyDebuggerSettings.getInstance();
        debuggerSettings.setWatchReturnValues(true);
      }

      @Override
      public void doFinally() {
        final PyDebuggerSettings debuggerSettings = PyDebuggerSettings.getInstance();
        debuggerSettings.setWatchReturnValues(false);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        stepOver();
        waitForPause();
        eval(PyDebugValue.RETURN_VALUES_PREFIX + "['bar'][0]").hasValue("1");
        stepOver();
        waitForPause();
        stepOver();
        waitForPause();
        eval(PyDebugValue.RETURN_VALUES_PREFIX + "['foo']").hasValue("33");
        resume();
      }

      @NotNull
      @Override
      public Set<String> getTags() {
        return ImmutableSet.of("-iron", "-python36");
      }
    });
  }

  @Test
  @Staging
  public void testSuspendAllThreadsPolicy() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test_two_threads.py") {
      @Override
      protected void init() {
        setMultiprocessDebug(true);
      }

      @Override
      public void before() throws Exception {
        toggleBreakpoint(getFilePath(getScriptName()), 17);
        setBreakpointSuspendPolicy(getProject(), 17, SuspendPolicy.ALL);
        setWaitForTermination(false);
      }

      @Override
      public void testing() throws Exception {
        waitForAllThreadsPause();
        eval("m").hasValue("42");
        assertNull(getRunningThread());
        resume();
      }

      @NotNull
      @Override
      public Set<String> getTags() {
        return ImmutableSet.of("-iron");
      }
    });
  }

  @Test
  @Staging
  public void testSuspendAllThreadsResume() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test_two_threads_resume.py") {
      @Override
      public void before() throws Exception {
        toggleBreakpoint(getFilePath(getScriptName()), 10);
        setBreakpointSuspendPolicy(getProject(), 10, SuspendPolicy.ALL);
        setWaitForTermination(false);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        eval("x").hasValue("12");
        resume();
        waitForPause();
        eval("x").hasValue("12");
        resume();
      }

      @NotNull
      @Override
      public Set<String> getTags() {
        return ImmutableSet.of("-iron");
      }
    });
  }

  @Test
  public void testSuspendOneThreadPolicy() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test_two_threads.py") {
      @Override
      public void before() throws Exception {
        toggleBreakpoint(getFilePath(getScriptName()), 17);
        setBreakpointSuspendPolicy(getProject(), 17, SuspendPolicy.THREAD);
        setWaitForTermination(false);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        eval("m").hasValue("42");
        assertEquals("Thread1", getRunningThread());
        resume();
      }
    });
  }

  @Test
  @Staging
  public void testShowReferringObjects() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test_ref.py") {
      @Override
      public void before() throws Exception {
        toggleBreakpoint(getFilePath(getScriptName()), 3);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        int numberOfReferringObjects = getNumberOfReferringObjects("l");
        assertEquals(3, numberOfReferringObjects);
      }

      @NotNull
      @Override
      public Set<String> getTags() {
        return ImmutableSet.of("-iron");
      }
    });
  }

  @Test
  public void testResume() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test_resume.py") {
      @Override
      public void before() throws Exception {
        toggleBreakpoint(getScriptName(), 1);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        eval("x").hasValue("1");
        resume();
        waitForPause();
        eval("x").hasValue("2");
        resume();
      }
    });
  }

  @Test
  public void testResumeAfterStepping() throws Exception {
    // This test case is important for frame evaluation debugging, because we reuse old tracing function for stepping and there were
    // some problems with switching between frame evaluation and tracing
    runPythonTest(new PyDebuggerTask("/debug", "test_resume_after_step.py") {
      @Override
      public void before() throws Exception {
        toggleBreakpoint(getScriptName(), 2);
        toggleBreakpoint(getScriptName(), 5);
        toggleBreakpoint(getScriptName(), 12);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        eval("a").hasValue("1");
        stepOver();
        waitForPause();
        stepOver();
        waitForPause();
        eval("c").hasValue("3");
        resume();
        waitForPause();
        eval("d").hasValue("4");
        resume();
        waitForPause();
        eval("t").hasValue("1");
        resume();
      }
    });
  }

  @Test
  public void testAddBreakWhileRunning() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test_resume_after_step.py") {
      @Override
      public void before() throws Exception {
        toggleBreakpoint(getScriptName(), 2);
        toggleBreakpoint(getScriptName(), 3);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        eval("a").hasValue("1");
        toggleBreakpoint(getScriptName(), 5);
        resume();
        waitForPause();
        eval("b").hasValue("2");
        resume();
        waitForPause();
        eval("d").hasValue("4");
        resume();
      }
    });
  }

  @Test
  public void testAddBreakAfterRemove() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test1.py") {
      @Override
      public void before() throws Exception {
        toggleBreakpoint(getScriptName(), 2);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        eval("i").hasValue("0");
        // remove break on line 2
        toggleBreakpoint(getScriptName(), 2);
        resume();
        // add break on line 2
        toggleBreakpoint(getScriptName(), 2);
        // check if break on line 2 works
        waitForPause();
        // remove break on line 2 again
        toggleBreakpoint(getScriptName(), 2);
        // add break on line 3
        toggleBreakpoint(getScriptName(), 3);
        resume();
        // check if break on line 3 works
        waitForPause();
        resume();
      }
    });
  }

  //TODO: That doesn't work now: case from test_continuation.py and test_continuation2.py are treated differently by interpreter
  // (first line is executed in first case and last line in second)

  @Staging
  @Test
  public void testBreakOnContinuationLine() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test_continuation.py") {
      @Override
      public void before() throws Exception {
        toggleBreakpoint(getScriptName(), 11);
        setWaitForTermination(false);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        eval("x").hasValue("0");
        stepOver();
        waitForPause();
        eval("x").hasValue("1");
        stepOver();
        waitForPause();
        stepOver();
        waitForPause();
        eval("x").hasValue("2");
      }

      @NotNull
      @Override
      public Set<String> getTags() {
        return ImmutableSet.of("-iron");
      }
    });
  }

  @Staging
  @Test
  public void testModuleInterpreterOption() throws Exception {
    runPythonTest(new BreakpointStopAndEvalTask("test1") {
      @Override
      public void before() throws Exception {
        toggleBreakpoint(getFilePath("test1.py"), 3);
        setScriptName("test1");
        setWaitForTermination(false);

        myRunConfiguration.setInterpreterOptions("-m");
      }
    });
  }
}

