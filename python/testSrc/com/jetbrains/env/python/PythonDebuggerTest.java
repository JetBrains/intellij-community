package com.jetbrains.env.python;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.xdebugger.XDebuggerTestUtil;
import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
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
import com.jetbrains.python.debugger.PyExceptionBreakpointProperties;
import com.jetbrains.python.debugger.PyExceptionBreakpointType;
import com.jetbrains.python.debugger.settings.PyDebuggerSettings;
import com.jetbrains.python.debugger.settings.PySteppingFilter;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import com.jetbrains.python.tools.sdkTools.SdkCreationType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * @author traff
 */

public class PythonDebuggerTest extends PyEnvTestCase {
  private class BreakpointStopAndEvalTask extends PyDebuggerTask {
    public BreakpointStopAndEvalTask(String scriptName) {
      super("/debug", scriptName);
    }

    @Override
    public void before() {
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
  public void testBreakpointStopAndEval() {
    runPythonTest(new BreakpointStopAndEvalTask("test1.py"));
  }

  @Test
  @Staging
  public void testPydevTests_Debugger() {
    unittests("tests_pydevd_python/test_debugger.py", null, true);
  }

  @Test
  @Staging
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
      protected PyUnitTestProcessRunner createProcessRunner() {
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
  public void testConditionalBreakpoint() {
    runPythonTest(new PyDebuggerTask("/debug", "test1.py") {
      @Override
      public void before() {
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
  @Staging
  public void testDebugConsole() {
    runPythonTest(new PyDebuggerTask("/debug", "test1.py") {
      @Override
      public void before() {
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
    });
  }

  @Test
  public void testDebugCompletion() {
    runPythonTest(new PyDebuggerTask("/debug", "test4.py") {
      @Override
      public void before() {
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
  public void testBreakpointLogExpression() {
    runPythonTest(new PyDebuggerTask("/debug", "test1.py") {
      @Override
      public void before() {
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
  public void testStepOver() {
    runPythonTest(new PyDebuggerTask("/debug", "test2.py") {
      @Override
      public void before() {
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
  public void testStepInto() {
    runPythonTest(new PyDebuggerTask("/debug", "test2.py") {
      @Override
      public void before() {
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
  public void testStepIntoMyCode() {
    runPythonTest(new PyDebuggerTask("/debug", "test_my_code.py") {

      @Override
      public void before() {
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
  public void testSmartStepInto() {
    runPythonTest(new PyDebuggerTask("/debug", "test3.py") {
      @Override
      public void before() {
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
  public void testSmartStepInto2() {
    runPythonTest(new PyDebuggerTask("/debug", "test3.py") {
      @Override
      public void before() {
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
  public void testInput() {
    runPythonTest(new PyDebuggerTask("/debug", "test_input.py") {
      @Override
      public void before() {
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
  @Staging
  public void testRunToLine() {
    runPythonTest(new PyDebuggerTask("/debug", "test_runtoline.py") {
      @Override
      public void before() {
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

  private static XBreakpoint addExceptionBreakpoint(IdeaProjectTestFixture fixture, PyExceptionBreakpointProperties properties) {
    return XDebuggerTestUtil.addBreakpoint(fixture.getProject(), PyExceptionBreakpointType.class, properties);
  }

  @Test
  public void testExceptionBreakpointOnTerminate() {
    runPythonTest(new PyDebuggerTask("/debug", "test_exceptbreak.py") {
      @Override
      public void before() {
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
  public void testExceptionBreakpointOnFirstRaise() {
    runPythonTest(new PyDebuggerTask("/debug", "test_exceptbreak.py") {
      @Override
      public void before() {
        createExceptionBreak(myFixture, false, true, true);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        eval("__exception__[0].__name__").hasValue("'IndexError'");
        resume();
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
                                          boolean ignoreLibraries,
                                          @Nullable String condition,
                                          @Nullable String logExpression) {
    XDebuggerTestUtil.removeAllBreakpoints(fixture.getProject());
    XDebuggerTestUtil.setDefaultBreakpointEnabled(fixture.getProject(), PyExceptionBreakpointType.class, false);

    PyExceptionBreakpointProperties properties = new PyExceptionBreakpointProperties("BaseException");
    properties.setNotifyOnTerminate(notifyOnTerminate);
    properties.setNotifyOnlyOnFirst(notifyOnFirst);
    properties.setIgnoreLibraries(ignoreLibraries);
    XBreakpoint exceptionBreakpoint = addExceptionBreakpoint(fixture, properties);
    if (condition != null) {
      exceptionBreakpoint.setCondition(condition);
    }
    if (logExpression != null) {
      exceptionBreakpoint.setLogExpression(logExpression);
    }
  }

  public static void createExceptionBreak(IdeaProjectTestFixture fixture,
                                          boolean notifyOnTerminate,
                                          boolean notifyOnFirst,
                                          boolean ignoreLibraries) {
    createExceptionBreak(fixture, notifyOnTerminate, notifyOnFirst, ignoreLibraries, null, null);
  }

  @Test
  public void testExceptionBreakpointIgnoreLibrariesOnRaise() {
    runPythonTest(new PyDebuggerTask("/debug", "test_ignore_lib.py") {

      @Override
      public void before() {
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
  public void testExceptionBreakpointIgnoreLibrariesOnTerminate() {
    runPythonTest(new PyDebuggerTask("/debug", "test_ignore_lib.py") {

      @Override
      public void before() {
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
  public void testExceptionBreakpointConditionOnRaise() {
    runPythonTest(new PyDebuggerTask("/debug", "test_exceptbreak.py") {

      @Override
      public void before() {
        createExceptionBreak(myFixture, false, true, true, "__exception__[0] == ZeroDivisionError", null);
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
        return ImmutableSet.of("-jython");
      }
    });
  }


  @Test
  public void testExceptionBreakpointConditionOnTerminate() {
    runPythonTest(new PyDebuggerTask("/debug", "test_exceptbreak.py") {

      @Override
      public void before() {
        createExceptionBreak(myFixture, true, false, false, "__exception__[0] == ZeroDivisionError", null);
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
        return ImmutableSet.of("-jython");
      }
    });
  }

  @Test
  public void testMultithreading() {
    runPythonTest(new PyDebuggerTask("/debug", "test_multithread.py") {
      @Override
      public void before() {
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
  public void testEggDebug() {
    runPythonTest(new PyDebuggerTask("/debug", "test_egg.py") {
      @Override
      public void before() {
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
  public void testWinEggDebug() {
    if (UsefulTestCase.IS_UNDER_TEAMCITY && !SystemInfo.isWindows) {
      return; // Only needs to run on windows
    }
    runPythonTest(new PyDebuggerTask("/debug", "test_winegg.py") {
      @Override
      public void before() {
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
  public void testWinLongName() {
    if (!SystemInfo.isWindows) {
      return; // Only needs to run on windows
    }
    runPythonTest(new PyDebuggerTask("/debug", "long_n~1.py") {
      @Override
      public void before() {
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
  public void testStepOverConditionalBreakpoint() {
    runPythonTest(new PyDebuggerTask("/debug", "test_stepOverCondition.py") {
      @Override
      public void before() {
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
  public void testMultiprocess() {
    runPythonTest(new PyDebuggerTask("/debug", "test_multiprocess.py") {
      @Override
      protected void init() {
        setMultiprocessDebug(true);
      }

      @Override
      public void before() {
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
  public void testMultiprocessingSubprocess() {
    runPythonTest(new PyDebuggerTask("/debug", "test_multiprocess_args.py") {
      @Override
      protected void init() {
        setMultiprocessDebug(true);
      }

      @Override
      public void before() {
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
  public void testPyQtQThreadInheritor() {
    if (UsefulTestCase.IS_UNDER_TEAMCITY && SystemInfo.isWindows) {
      return; //Don't run under Windows
    }

    runPythonTest(new PyDebuggerTask("/debug", "test_pyqt1.py") {
      @Override
      protected void init() {
        setMultiprocessDebug(true);
      }

      @Override
      public void before() {
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
  public void testPyQtMoveToThread() {
    if (UsefulTestCase.IS_UNDER_TEAMCITY && SystemInfo.isWindows) {
      return; //Don't run under Windows
    }

    runPythonTest(new PyDebuggerTask("/debug", "test_pyqt2.py") {
      @Override
      protected void init() {
        setMultiprocessDebug(true);
      }

      @Override
      public void before() {
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
  public void testPyQtQRunnableInheritor() {
    if (UsefulTestCase.IS_UNDER_TEAMCITY && SystemInfo.isWindows) {
      return; //Don't run under Windows
    }

    runPythonTest(new PyDebuggerTask("/debug", "test_pyqt3.py") {
      @Override
      protected void init() {
        setMultiprocessDebug(true);
      }

      @Override
      public void before() {
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
  public void testStepOverYieldFrom() {
    runPythonTest(new PyDebuggerTask("/debug", "test_step_over_yield.py") {
      @Override
      protected void init() {
        setMultiprocessDebug(true);
      }

      @Override
      public void before() {
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
  public void testSteppingFilter() {
    runPythonTest(new PyDebuggerTask("/debug", "test_stepping_filter.py") {

      @Override
      public void before() {
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
  public void testReturnValues() {
    runPythonTest(new PyDebuggerTask("/debug", "test_return_values.py") {
      @Override
      public void before() {
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
  public void testSuspendAllThreadsPolicy() {
    runPythonTest(new PyDebuggerTask("/debug", "test_two_threads.py") {
      @Override
      protected void init() {
        setMultiprocessDebug(true);
      }

      @Override
      public void before() {
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
  public void testSuspendAllThreadsResume() {
    runPythonTest(new PyDebuggerTask("/debug", "test_two_threads_resume.py") {
      @Override
      public void before() {
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
  public void testSuspendOneThreadPolicy() {
    runPythonTest(new PyDebuggerTask("/debug", "test_two_threads.py") {
      @Override
      public void before() {
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
  public void testShowReferringObjects() {
    runPythonTest(new PyDebuggerTask("/debug", "test_ref.py") {
      @Override
      public void before() {
        toggleBreakpoint(getFilePath(getScriptName()), 3);
      }

      private String getRefWithWordInName(List<String> referrersNames, String word) {
        return referrersNames.stream().filter(x -> x.contains(word)).findFirst().get();
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        List<String> referrersNames = getNumberOfReferringObjects("l");
        assertNotNull(getRefWithWordInName(referrersNames, "frame"));
        assertNotNull(getRefWithWordInName(referrersNames, "module"));
        assertNotNull(getRefWithWordInName(referrersNames, "dict"));
      }

      @NotNull
      @Override
      public Set<String> getTags() {
        return ImmutableSet.of("-iron");
      }
    });
  }

  @Test
  public void testResume() {
    runPythonTest(new PyDebuggerTask("/debug", "test_resume.py") {
      @Override
      public void before() {
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
  public void testResumeAfterStepping() {
    // This test case is important for frame evaluation debugging, because we reuse old tracing function for stepping and there were
    // some problems with switching between frame evaluation and tracing
    runPythonTest(new PyDebuggerTask("/debug", "test_resume_after_step.py") {
      @Override
      public void before() {
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
  public void testAddBreakWhileRunning() {
    runPythonTest(new PyDebuggerTask("/debug", "test_resume_after_step.py") {
      @Override
      public void before() {
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
  public void testAddBreakAfterRemove() {
    runPythonTest(new PyDebuggerTask("/debug", "test1.py") {
      @Override
      public void before() {
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

  @Test
  public void testSetNextStatement() {
    runPythonTest(new PyDebuggerTask("/debug", "test_set_next_statement.py") {
      @Override
      public void before() {
        toggleBreakpoint(getFilePath(getScriptName()), 1);
        toggleBreakpoint(getFilePath(getScriptName()), 6);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        eval("x").hasValue("0");
        // jump on a top level
        Pair<Boolean, String> pair = setNextStatement(7);
        waitForPause();
        assertTrue(pair.first);
        eval("x").hasValue("1");
        // try to jump into a loop
        pair = setNextStatement(9);
        // do not wait for pause here, because we don't refresh suspension for incorrect jumps
        assertFalse(pair.first);
        assertTrue(pair.second.startsWith("Error:"));
        resume();
        waitForPause();
        eval("a").hasValue("3");
        // jump inside a function
        pair = setNextStatement(2);
        waitForPause();
        assertTrue(pair.first);
        eval("a").hasValue("6");
        resume();
      }
    });
  }

  @Test
  public void testLoadValuesAsync() {
    runPythonTest(new PyDebuggerTask("/debug", "test_async_eval.py") {
      @Override
      public void before() {
        toggleBreakpoint(getFilePath(getScriptName()), 14);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        List<PyDebugValue> frameVariables = loadFrame();
        String result = computeValueAsync(frameVariables, "f");
        assertEquals("foo", result);

        List<PyDebugValue> listChildren = loadChildren(frameVariables, "l");
        result = computeValueAsync(listChildren, "0");
        assertEquals("list", result);
      }
    });
  }

  //TODO: That doesn't work now: case from test_continuation.py and test_continuation2.py are treated differently by interpreter
  // (first line is executed in first case and last line in second)

  @Staging
  @Test
  public void testBreakOnContinuationLine() {
    runPythonTest(new PyDebuggerTask("/debug", "test_continuation.py") {
      @Override
      public void before() {
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

  @Test
  public void testModuleInterpreterOption() {
    runPythonTest(new BreakpointStopAndEvalTask("test1") {
      @Override
      public void before() {
        toggleBreakpoint(getFilePath("test1.py"), 3);
        setScriptName("test1");
        setWaitForTermination(false);

        myRunConfiguration.setModuleMode(true);
      }
    });
  }

  @Staging
  @Test
  public void testShowCommandline() {
    runPythonTest(new PyDebuggerTask("/debug", "test2.py") {
      @Override
      public void before() {
        toggleBreakpoint(getFilePath(getScriptName()), 6);
        setWaitForTermination(false);

        myRunConfiguration.setShowCommandLineAfterwards(true);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        eval("z").hasValue("1");
        resume();
        consoleExec("z");
        waitForOutput("2");
      }

      @Override
      public void doFinally() {
        myRunConfiguration.setShowCommandLineAfterwards(false);
      }
    });
  }

  @Staging
  @Test
  public void testShowCommandlineModule() {
    runPythonTest(new PyDebuggerTask("/debug", "test2") {
      @Override
      public void before() {
        toggleBreakpoint(getFilePath("test2.py"), 6);
        setScriptName("test2");
        setWaitForTermination(false);

        myRunConfiguration.setShowCommandLineAfterwards(true);
        myRunConfiguration.setModuleMode(true);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        eval("z").hasValue("1");
        resume();
        consoleExec("foo(3)");
        waitForOutput("5");
      }

      @Override
      public void doFinally() {
        myRunConfiguration.setShowCommandLineAfterwards(false);
        myRunConfiguration.setModuleMode(false);
      }
    });
  }
}

