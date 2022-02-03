// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.env.debug;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.xdebugger.XDebuggerTestUtil;
import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.python.console.pydev.PydevCompletionVariant;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyExceptionBreakpointProperties;
import com.jetbrains.python.debugger.PyExceptionBreakpointType;
import com.jetbrains.python.debugger.settings.PyDebuggerSettings;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assume;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class PythonDebuggerTest extends PyEnvTestCase {
  private static class BreakpointStopAndEvalTask extends PyDebuggerTask {
    BreakpointStopAndEvalTask(String scriptName) {
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
        waitForOutput("message=\"python-exceptions.ZeroDivisionError\"", "message=\"python-builtins.ZeroDivisionError\"");
        waitForOutput("Traceback (most recent call last):");
        waitForOutput("line 7, in foo");
        waitForOutput("return 1 / x");
        waitForOutput("ZeroDivisionError: ");
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
    PyBaseDebuggerTask.addExceptionBreakpoint(fixture, properties);
    properties = new PyExceptionBreakpointProperties("builtins.ZeroDivisionError"); //for python 3
    properties.setNotifyOnTerminate(notifyOnTerminate);
    properties.setNotifyOnlyOnFirst(notifyOnFirst);
    properties.setIgnoreLibraries(ignoreLibraries);
    PyBaseDebuggerTask.addExceptionBreakpoint(fixture, properties);
  }

  private static void createDefaultExceptionBreakpoint(IdeaProjectTestFixture fixture) {
    XDebuggerTestUtil.removeAllBreakpoints(fixture.getProject());
    XDebuggerTestUtil.setDefaultBreakpointEnabled(fixture.getProject(), PyExceptionBreakpointType.class, true);
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
        waitForOutput("message=\"python-BaseException\"");
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
        waitForOutput("message=\"python-BaseException\"");
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
  public void testExceptionBreakpointIgnoreInUnittestModule() {
    runPythonTest(new PyDebuggerTask("/debug", "test_ignore_exceptions_in_unittest.py") {

      @Override
      public void before() {
        createExceptionBreak(myFixture, false, true, true);
        toggleBreakpoint(getFilePath(getScriptName()), 2);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
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
        waitForOutput("message=\"python-BaseException\"");
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
        waitForOutput("message=\"python-BaseException\"");
        eval("__exception__[0].__name__").hasValue("'ZeroDivisionError'");
        resume();
        waitForTerminate();
      }

      @NotNull
      @Override
      public Set<String> getTags() {
        return ImmutableSet.of("-jython", "-iron");
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
        waitForOutput("message=\"python-BaseException\"");
        eval("__exception__[0].__name__").hasValue("'ZeroDivisionError'");
        resume();
        waitForTerminate();
      }

      @NotNull
      @Override
      public Set<String> getTags() {
        return ImmutableSet.of("-jython", "-iron");
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
  public void testEggDebug() {
    runPythonTest(new PyDebuggerTask("/debug", "test_egg.py") {
      @Override
      public void before() {
        String egg = getFilePath("Adder-0.1.egg");
        toggleBreakpointInEgg(egg, "adder/adder.py", 2);
        PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(getRunConfiguration().getSdkHome());
        if (flavor != null) {
          flavor.initPythonPath(Lists.newArrayList(egg), true, getRunConfiguration().getEnvs());
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
    Assume.assumeFalse("Only needs to run on windows", UsefulTestCase.IS_UNDER_TEAMCITY && !SystemInfo.isWindows);
    runPythonTest(new PyDebuggerTask("/debug", "test_winegg.py") {
      @Override
      public void before() {
        String egg = getFilePath("wintestegg-0.1.egg");
        toggleBreakpointInEgg(egg, "eggxample/lower_case.py", 2);
        toggleBreakpointInEgg(egg, "eggxample/MIXED_case.py", 2);

        PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(getRunConfiguration().getSdkHome());
        if (flavor != null) {
          flavor.initPythonPath(Lists.newArrayList(egg), true, getRunConfiguration().getEnvs());
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
        return ImmutableSet.of("-iron", "-python3.6");
      }
    });
  }

  @Test
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
        removeBreakpoint(getScriptName(), 2);
        resume();
        // add break on line 2
        toggleBreakpoint(getScriptName(), 2);
        // check if break on line 2 works
        waitForPause();
        // remove break on line 2 again
        removeBreakpoint(getScriptName(), 2);
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
        eval("x").hasValue("0");
        // try to jump into a loop
        pair = setNextStatement(9);
        // do not wait for pause here, because we don't refresh suspension for incorrect jumps
        assertFalse(pair.first);
        assertTrue(pair.second.startsWith("Error:"));
        stepOver();
        waitForPause();
        eval("x").hasValue("2");
        resume();
        waitForPause();
        eval("a").hasValue("2");
        // jump inside a function
        pair = setNextStatement(2);
        waitForPause();
        assertTrue(pair.first);
        eval("a").hasValue("2");
        resume();
      }

      @NotNull
      @Override
      public Set<String> getTags() {
        return ImmutableSet.of("-iron", "-python3.8"); // PY-38604
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
        return ImmutableSet.of("-iron", "-python3.8");  // PY-38603
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

        getRunConfiguration().setModuleMode(true);
      }
    });
  }

  @Test
  public void testShowCommandline() {
    runPythonTest(new PyDebuggerTask("/debug", "test2.py") {
      @Override
      public void before() {
        toggleBreakpoint(getFilePath(getScriptName()), 6);
        setWaitForTermination(false);

        getRunConfiguration().setShowCommandLineAfterwards(true);
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
        getRunConfiguration().setShowCommandLineAfterwards(false);
      }
    });
  }

  @Test
  public void testShowCommandlineModule() {
    runPythonTest(new PyDebuggerTask("/debug", "test2") {
      @Override
      public void before() {
        toggleBreakpoint(getFilePath("test2.py"), 6);
        setScriptName("test2");
        setWaitForTermination(false);

        getRunConfiguration().setShowCommandLineAfterwards(true);
        getRunConfiguration().setModuleMode(true);
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
        getRunConfiguration().setShowCommandLineAfterwards(false);
        getRunConfiguration().setModuleMode(false);
      }
    });
  }

  @Test
  public void testBuiltinBreakpoint() {
    runPythonTest(new PyDebuggerTask("/debug", "test_builtin_break.py") {
      @Override
      public void before() {
        toggleBreakpoint(getFilePath(getScriptName()),2);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        resume();
        waitForPause();
        eval("a").hasValue("1");
        resume();
      }

      @NotNull
      @Override
      public Set<String> getTags() {
        return ImmutableSet.of("python3.7");
      }
    });
  }

  @Test
  public void testTypeHandler() {
    runPythonTest(new PyDebuggerTask("/debug", "test_type_handler.py") {
      @Override
      public void before() {
        toggleBreakpoint(getFilePath(getScriptName()), 11);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        eval("s1").hasValue("'\\\\'");
        eval("s2").hasValue("'\\\\\\\\'");
        eval("s3").hasValue("\"'\"");
        eval("s4").hasValue("'\"'");
        eval("s5").hasValue("'\n'");
        eval("s6").hasValue("\"'foo'bar\nbaz\\\\\"");
        eval("s7").hasValue("'^\\\\w+$'");
        eval("s8").hasValue("\"'459'\"");
        eval("s9").hasValue("'459'");
        eval("s10").hasValue("'\u2764'");
      }
    });
  }

  @Test
  public void testLargeCollectionsLoading() {
    runPythonTest(new PyDebuggerTask("/debug", "test_large_collections.py") {
      @Override
      public void before() { toggleBreakpoint(getFilePath(getScriptName()), 15); }

      @Override
      public void testing() throws Exception {
        waitForPause();
        List<PyDebugValue> frameVariables = loadFrame();

        // The effective maximum number of the debugger returns is MAX_ITEMS_TO_HANDLE
        // plus the __len__ attribute.
        final int effectiveMaxItemsNumber = MAX_ITEMS_TO_HANDLE + 1;

        // Large list.
        PyDebugValue L = findDebugValueByName(frameVariables, "L");

        XValueChildrenList children = loadVariable(L);
        int collectionLength = 1000;

        assertEquals(effectiveMaxItemsNumber, children.size());
        for (int i = 0; i < MAX_ITEMS_TO_HANDLE; i++) {
          assertTrue(hasChildWithName(children, formatStr(i, collectionLength)));
          assertTrue(hasChildWithValue(children, i));
        }

        L.setOffset(600);
        children = loadVariable(L);
        assertEquals(effectiveMaxItemsNumber, children.size());
        for (int i = 600; i < MAX_ITEMS_TO_HANDLE; i++) {
          assertTrue(hasChildWithName(children, formatStr(i, collectionLength)));
          assertTrue(hasChildWithValue(children, i));
        }

        L.setOffset(900);
        children = loadVariable(L);
        assertEquals(101, children.size());
        for (int i = 900; i < collectionLength; i++) {
          assertTrue(hasChildWithName(children, formatStr(i, collectionLength)));
          assertTrue(hasChildWithValue(children, i));
        }

        // Large dict.
        PyDebugValue D = findDebugValueByName(frameVariables, "D");

        children = loadVariable(D);
        collectionLength = 1000;

        assertEquals(effectiveMaxItemsNumber, children.size());
        for (int i = 0; i < MAX_ITEMS_TO_HANDLE; i++) {
          assertTrue(hasChildWithName(children, i));
          assertTrue(hasChildWithValue(children, i));
        }

        D.setOffset(600);
        children = loadVariable(D);
        assertEquals(effectiveMaxItemsNumber, children.size());
        for (int i = 600; i < MAX_ITEMS_TO_HANDLE; i++) {
          assertTrue(hasChildWithName(children, i));
          assertTrue(hasChildWithValue(children, i));
        }

        D.setOffset(900);
        children = loadVariable(D);
        assertEquals(101, children.size());
        for (int i = 900; i < collectionLength; i++) {
          assertTrue(hasChildWithName(children, i));
          assertTrue(hasChildWithValue(children, i));
        }

        // Large set.
        PyDebugValue S = findDebugValueByName(frameVariables, "S");

        children = loadVariable(S);
        collectionLength = 1000;

        assertEquals(effectiveMaxItemsNumber, children.size());
        for (int i = 0; i < MAX_ITEMS_TO_HANDLE; i++) {
          assertTrue(hasChildWithValue(children, i));
        }

        S.setOffset(600);
        children = loadVariable(S);
        assertEquals(effectiveMaxItemsNumber, children.size());
        for (int i = 600; i < MAX_ITEMS_TO_HANDLE; i++) {
          assertTrue(hasChildWithValue(children, i));
        }

        S.setOffset(900);
        children = loadVariable(S);
        assertEquals(101, children.size());
        for (int i = 900; i < collectionLength; i++) {
          assertTrue(hasChildWithValue(children, i));
        }

        // Large deque.
        PyDebugValue dq = findDebugValueByName(frameVariables, "dq");

        children = loadVariable(dq);
        collectionLength = 1000;

        assertEquals(effectiveMaxItemsNumber + 1, children.size()); // one extra child for maxlen
        for (int i = 1; i < MAX_ITEMS_TO_HANDLE; i++) {
          assertTrue(hasChildWithName(children, formatStr(i, collectionLength)));
          assertTrue(hasChildWithValue(children, i));
        }

        dq.setOffset(600);
        children = loadVariable(dq);
        assertEquals(effectiveMaxItemsNumber, children.size());
        for (int i = 600; i < MAX_ITEMS_TO_HANDLE; i++) {
          assertTrue(hasChildWithName(children, formatStr(i, collectionLength)));
          assertTrue(hasChildWithValue(children, i));
        }

        dq.setOffset(900);
        children = loadVariable(dq);
        assertEquals(101, children.size());
        for (int i = 900; i < collectionLength; i++) {
          assertTrue(hasChildWithName(children, formatStr(i, collectionLength)));
          assertTrue(hasChildWithValue(children, i));
        }
      }
    });
  }

  @Test
  public void testLargeNumpyArraysLoading() {
    runPythonTest(new PyDebuggerTask("/debug", "test_large_numpy_arrays.py") {
      @NotNull
      @Override
      public Set<String> getTags() {
        return ImmutableSet.of("pandas");
      }

      @Override
      public void before() { toggleBreakpoint(getFilePath(getScriptName()), 9); }

      @Override
      public void testing() throws Exception {
        waitForPause();
        List<PyDebugValue> frameVariables = loadFrame();

        int collectionLength = 1000;

        // NumPy array

        PyDebugValue nd = findDebugValueByName(frameVariables, "nd");
        XValueChildrenList children = loadVariable(nd);

        assertEquals("min", children.getName(0));
        assertEquals("max", children.getName(1));
        assertEquals("shape", children.getName(2));
        assertEquals("dtype", children.getName(3));
        assertEquals("size", children.getName(4));
        assertEquals("array", children.getName(5));

        PyDebugValue array = (PyDebugValue) children.getValue(5);

        children = loadVariable(array);
        assertEquals(MAX_ITEMS_TO_HANDLE + 1, children.size());

        for (int i = 0; i < MAX_ITEMS_TO_HANDLE; i++) {
          assertTrue(hasChildWithName(children, formatStr(i, collectionLength)));
        }

        array.setOffset(950);
        children = loadVariable(array);

        assertEquals(51, children.size());

        for (int i = 950; i < collectionLength; i++) {
          assertTrue(hasChildWithName(children, formatStr(i, collectionLength)));
        }

        // Pandas series

        PyDebugValue s = findDebugValueByName(frameVariables, "s");
        children = loadVariable(s);
        PyDebugValue values = (PyDebugValue) children.getValue(children.size() - 1);
        children = loadVariable(values);
        array = (PyDebugValue) children.getValue(children.size() - 1);
        children = loadVariable(array);

        assertEquals(MAX_ITEMS_TO_HANDLE + 1, children.size());

        for (int i = 0; i < MAX_ITEMS_TO_HANDLE; i++) {
          assertTrue(hasChildWithName(children, formatStr(i, collectionLength)));
        }

        array.setOffset(950);
        children = loadVariable(array);

        assertEquals(51, children.size());

        for (int i = 950; i < collectionLength; i++) {
          assertTrue(hasChildWithName(children, formatStr(i, collectionLength)));
        }

        // Pandas data frame

        PyDebugValue df = findDebugValueByName(frameVariables, "df");
        children = loadVariable(df);
        values = (PyDebugValue) children.getValue(children.size() - 1);
        children = loadVariable(values);
        array = (PyDebugValue) children.getValue(children.size() - 1);
        children = loadVariable(array);

        assertEquals(MAX_ITEMS_TO_HANDLE + 1, children.size());

        for (int i = 0; i < MAX_ITEMS_TO_HANDLE; i++) {
          assertTrue(hasChildWithName(children, formatStr(i, collectionLength)));
        }

        array.setOffset(950);
        children = loadVariable(array);

        assertEquals(51, children.size());

        for (int i = 950; i < collectionLength; i++) {
          assertTrue(hasChildWithName(children, formatStr(i, collectionLength)));
        }
      }
    });
  }

  @Test
  public void testWarningsSuppressing() {
    runPythonTest(new PyDebuggerTask("/debug", "test_warnings_suppressing.py") {
      @Override
      public void before() {
        toggleBreakpoint(getFilePath(getScriptName()), 15);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        List<PyDebugValue> frameVariables = loadFrame();
        PyDebugValue obj = findDebugValueByName(frameVariables, "obj");
        loadVariable(obj);
        String out = output();
        assertTrue(out.contains("This warning should appear in the output."));
        assertFalse(out.contains("This property is deprecated!"));
        resume();
        waitForOutput("This property is deprecated!");
      }

      @NotNull
      @Override
      public Set<String> getTags() {
        return ImmutableSet.of("-iron"); // PY-37793
      }
    });
  }

  @Test
  public void testExecutableScriptDebug() {

    Assume.assumeFalse("Don't run under Windows", UsefulTestCase.IS_UNDER_TEAMCITY && SystemInfo.isWindows);

    runPythonTest(new PyDebuggerTask("/debug", "test_executable_script_debug.py") {
      @Override
      protected void init() {
        setMultiprocessDebug(true);
      }

      @Override
      public void before() {
        toggleBreakpoint(getFilePath("test_executable_script_debug_helper.py"), 4);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        eval("x").hasValue("42");
        resume();
        waitForOutput("Subprocess exited with return code: 0");
      }
    });
  }

  @Test
  public void testDebugConsolePytest() {
    runPythonTest(new PyDebuggerTask("/debug", "test_debug_console_pytest.py") {
      @Override
      public void before() {
        toggleBreakpoint(getFilePath(getScriptName()), 4);
      }

      @Override
      protected boolean usePytestRunner() {
        return true;
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        eval("a").hasValue("1");
        consoleExec("print('a = %s' % a)");
        waitForOutput("a = 1");
      }

      @NotNull
      @Override
      public Set<String> getTags() {
        return Sets.newHashSet("pytest");
      }
    });
  }

  @Test
  public void testDontStopOnSystemExit() {
    runPythonTest(new PyDebuggerTask("/debug", "test_sys_exit.py") {
      @Override
      public void before() {
        toggleBreakpoint(getFilePath(getScriptName()), 3);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        setProcessCanTerminate(true);
        resume();
        waitForOutput("Process finished with exit code 0");
      }

      @NotNull
      @Override
      public Set<String> getTags() {
        return ImmutableSet.of("-iron"); // PY-37791
      }
    });
  }

  private static class PyDebuggerTaskTagAware extends PyDebuggerTask {

    private PyDebuggerTaskTagAware(@Nullable String relativeTestDataPath, String scriptName) {
      super(relativeTestDataPath, scriptName);
    }

    public boolean hasTag(String tag) throws NullPointerException {
      String env = Paths.get(myRunConfiguration.getSdkHome()).getParent().getParent().toString();
      return envTags.get(env).stream().anyMatch((t) -> t.startsWith(tag));
    }
  }

  @Test
  public void testExecAndSpawnWithBytesArgs() {

    Assume.assumeFalse("Don't run under Windows", UsefulTestCase.IS_UNDER_TEAMCITY && SystemInfo.isWindows);

    final class ExecAndSpawnWithBytesArgsTask extends PyDebuggerTaskTagAware {

      private final static String BYTES_ARGS_WARNING = "pydev debugger: bytes arguments were passed to a new process creation function. " +
                                                       "Breakpoints may not work correctly.\n";
      private final static String PYTHON2_TAG = "python2";

      private ExecAndSpawnWithBytesArgsTask(@Nullable String relativeTestDataPath, String scriptName) {
        super(relativeTestDataPath, scriptName);
      }

      private boolean hasPython2Tag() throws NullPointerException {
          return hasTag(PYTHON2_TAG);
      }

      @Override
      protected void init() {
        setMultiprocessDebug(true);
      }

      @Override
      public void before() {
        toggleBreakpoint(getFilePath(getScriptName()), 4);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        setProcessCanTerminate(true);
        resume();
        try {
          if (hasPython2Tag()) {
            assertFalse(output().contains(BYTES_ARGS_WARNING));
          }
          else
            waitForOutput(BYTES_ARGS_WARNING);
        }
        catch (NullPointerException e) {
          fail("Error while checking if the env has the " + PYTHON2_TAG + " tag.");
        }
      }

      @NotNull
      @Override
      public Set<String> getTags() {
        return ImmutableSet.of("-iron", "-jython");
      }
    }

    Arrays.asList("test_call_exec_with_bytes_args.py", "test_call_spawn_with_bytes_args.py").forEach(
      (script) -> runPythonTest(new ExecAndSpawnWithBytesArgsTask("/debug", script))
    );
  }

  @Test
  public void testListComprehension() {
    runPythonTest(new PyDebuggerTask("/debug", "test_list_comprehension.py") {
      @Override
      public void before() {
        toggleBreakpoint(getFilePath(getScriptName()), 2);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        resume();
        waitForPause();
        List<PyDebugValue> frameVariables = loadFrame();
        assertTrue(findDebugValueByName(frameVariables,".0").getType().endsWith("_iterator"));
        eval(".0");
        // Different Python versions have different types of an internal list comprehension loop. Whatever the type is, we shouldn't get
        // an evaluating error.
        assertFalse(output().contains("Error evaluating"));
        toggleBreakpoint(getFilePath(getScriptName()), 2);
        resume();
      }

      @NotNull
      @Override
      public Set<String> getTags() {
        // Remove this after PY-36229 is fixed.
        return ImmutableSet.of("python3");
      }
    });
  }

  @Test
  public void testCallingSettraceWarning() {
    runPythonTest(new PyDebuggerTask("/debug", "test_calling_settrace_warning.py") {
      @Override
      public void before() {
        toggleBreakpoint(getFilePath(getScriptName()), 2);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        resume();
        waitForTerminate();
        outputContains("PYDEV DEBUGGER WARNING:\nsys.settrace() should not be used when the debugger is being used.");
      }

      @NotNull
      @Override
      public Set<String> getTags() {
        return ImmutableSet.of("-iron"); // PY-37796
      }
    });
  }

  @Test
  public void testStopsOnSyntaxError() {
    runPythonTest(new PyDebuggerTask("/debug", "test_syntax_error.py") {
      @Override
      public void before() {
        createDefaultExceptionBreakpoint(myFixture);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        try {
          resume();
          waitForTerminate();
        }
        catch (AssertionError e) {
          if (!e.getMessage().contains("SyntaxError: invalid syntax")) throw e;
        }
      }

      @NotNull
      @Override
      public Set<String> getTags() {
        return ImmutableSet.of("-iron"); // PY-36367
      }
    });
  }

  @Test
  public void testCodeEvaluationWithGeneratorExpression() {
    runPythonTest(new PyDebuggerTaskTagAware("/debug", "test_code_eval_with_generator_expr.py") {

      private final static String PYTHON2_TAG = "python2";

      @Override
      public void before() throws Exception {
        toggleBreakpoint(getFilePath(getScriptName()), 8);
      }

      @Override
      public void testing() throws Exception {
        String[] expectedOutput = ("[True] \t [True]\n" +
                                   "[False] \t [False]\n" +
                                   "[None] \t [None]").split("\n");
        waitForPause();
        for (String line : expectedOutput) {
          waitForOutput(line);
        }
        consoleExec("TFN = [True, False, None]\n" +
                    "for q in TFN:\n" +
                    "    gen = (c for c in TFN if c == q)\n" +
                    "    lcomp = [c for c in TFN if c == q]\n" +
                    "    print(list(gen), \"\\t\", list(lcomp))");
        if (hasPython2Tag()) {
          // Python 2 formats the output slightly differently.
          expectedOutput = ("([True], '\\t', [True])\n" +
                            "([False], '\\t', [False])\n" +
                            "([None], '\\t', [None])").split("\n");
          for (String line : expectedOutput) {
            waitForOutput(line);
          }
        }
        else {
          for (String line : expectedOutput) {
            waitForOutput(line, 2);
          }
        }
        consoleExec("def g():\n" +
                    "    print(\"Foo, bar, baz\")\n" +
                    "def f():\n" +
                    "    g()\n" +
                    "f()");
        waitForOutput("Foo, bar, baz");
        resume();
      }

      private boolean hasPython2Tag() throws NullPointerException {
        return hasTag(PYTHON2_TAG);
      }
    });
  }

  @Test
  public void testCodeEvaluationWithPandas() {
    runPythonTest(new PyDebuggerTask("/debug", "test_dataframe.py") {
      @Override
      public void before() throws Exception {
        toggleBreakpoint(getFilePath(getScriptName()), 30);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        consoleExec("x = 42");
        resume();
        waitForTerminate();
        assertFalse(output().contains("ValueError: The truth value of a DataFrame is ambiguous."));
      }

      @NotNull
      @Override
      public Set<String> getTags() {
        return ImmutableSet.of("pandas");
      }
    });
  }

  @Test
  public void testNoDebuggerRelatedStacktraceOnDebuggerStop() {
    runPythonTest(new PyDebuggerTask("/debug", "test1.py") {
      @Override
      public void before() {
        toggleBreakpoint(getFilePath(getScriptName()), 2);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        setProcessCanTerminate(true);
        resume();
        disposeDebugProcess();
        waitForOutput("Process finished with exit code 1");
        assertTrue("Output should contain KeyboardInterrupt as the exit reason when debugger is stopped",
                   output().contains("KeyboardInterrupt"));
        assertFalse("Output shouldn't contain debugger related stacktrace when debugger is stopped",
                    output().contains("pydevd.py\", line "));
      }
    });
  }

  @Test
  public void testCollectionsShapes() {
    runPythonTest(new PyDebuggerTask("/debug", "test_shapes.py") {
      @Override
      public void before() {
        toggleBreakpoint(getFilePath(getScriptName()), 39);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        List<PyDebugValue> frameVariables = loadFrame();
        PyDebugValue var = findDebugValueByName(frameVariables, "list1");
        assertEquals("120", var.getShape());
        var = findDebugValueByName(frameVariables, "dict1");
        assertEquals("2", var.getShape());
        var = findDebugValueByName(frameVariables, "custom");
        assertEquals("5", var.getShape());
        var = findDebugValueByName(frameVariables, "df1");
        assertEquals("(3, 6)", var.getShape());
        var = findDebugValueByName(frameVariables, "n_array");
        assertEquals("(3, 2)", var.getShape());
        var = findDebugValueByName(frameVariables, "series");
        assertEquals("(5,)", var.getShape());

        var = findDebugValueByName(frameVariables, "custom_shape");
        assertEquals("(3,)", var.getShape());
        var = findDebugValueByName(frameVariables, "custom_shape2");
        assertEquals("(2, 3)", var.getShape());
        resume();
        waitForTerminate();
      }

      @NotNull
      @Override
      public Set<String> getTags() {
        return ImmutableSet.of("pandas");
      }
    });
  }

  @Test
  public void testPathWithAmpersand() {
    runPythonTest(new PyDebuggerTask("/debug", "test_path_with_&.py") {
      @Override
      public void before() {
        toggleBreakpoint(getFilePath(getScriptName()), 3);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        // Source position can be `null` because of troubles while decoding the message from the debugger.
        // The troubles can be a result of an unescaped symbol, wrongly encoded message, etc.
        assertNotNull(getCurrentStackFrame().getSourcePosition());
        resume();
        waitForTerminate();
      }
    });
  }
}
