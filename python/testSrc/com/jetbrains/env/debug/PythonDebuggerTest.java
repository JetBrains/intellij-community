// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.env.debug;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XDebuggerTestUtil;
import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.python.console.pydev.PydevCompletionVariant;
import com.jetbrains.python.debugger.*;
import com.jetbrains.python.debugger.pydev.ProcessDebugger;
import com.jetbrains.python.debugger.settings.PyDebuggerSettings;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assume;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.jetbrains.python.debugger.PyDebugUtilsKt.getQuotingString;
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
    runPythonTest(new BreakpointStopAndEvalTask("test1.py") {
      @Override
      public void before() {
        toggleBreakpoint(getFilePath(getScriptName()), 6);
        setWaitForTermination(false);
      }
    });
  }

  @Test
  public void testConditionalBreakpoint() {
    runPythonTest(new PyDebuggerTask("/debug", "test1.py") {
      @Override
      public void before() {
        toggleBreakpoint(getFilePath(getScriptName()), 7);
        XDebuggerTestUtil.setBreakpointCondition(getProject(), 7, "i == 1 or i == 11 or i == 111");
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
        toggleBreakpoint(getFilePath(getScriptName()), 7);
        XDebuggerTestUtil.setBreakpointLogExpression(getProject(), 7, "'i = %d'%i");
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

      @Override
      public boolean isLanguageLevelSupported(@NotNull final LanguageLevel level) {
        return level.compareTo(LanguageLevel.PYTHON27) > 0;
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
        createExceptionBreakZeroDivisionError(myFixture);
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
        return Collections.singleton("-iron");
      }
    });
  }

  private static void createExceptionBreakZeroDivisionError(IdeaProjectTestFixture fixture) {
    XDebuggerTestUtil.removeAllBreakpoints(fixture.getProject());
    XDebuggerTestUtil.setDefaultBreakpointEnabled(fixture.getProject(), PyExceptionBreakpointType.class, false);

    PyExceptionBreakpointProperties properties = new PyExceptionBreakpointProperties("exceptions.ZeroDivisionError");
    properties.setNotifyOnTerminate(true);
    properties.setNotifyOnlyOnFirst(false);
    properties.setIgnoreLibraries(false);
    PyBaseDebuggerTask.addExceptionBreakpoint(fixture, properties);
    properties = new PyExceptionBreakpointProperties("builtins.ZeroDivisionError"); //for python 3
    properties.setNotifyOnTerminate(true);
    properties.setNotifyOnlyOnFirst(false);
    properties.setIgnoreLibraries(false);
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
        return Collections.singleton("-iron");
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
        return Collections.singleton("-iron");
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
    });
  }

  @Test
  public void testEggDebug() {
    runPythonTest(new PyDebuggerTask("/debug", "test_egg.py") {
      @Override
      public void before() {
        String egg = getFilePath("Adder-0.1.egg");
        toggleBreakpointInEgg(egg, "adder/adder.py", 2);
        PythonSdkFlavor<?> flavor = PythonSdkFlavor.getFlavor(getRunConfiguration().getSdk());
        if (flavor != null) {
          flavor.initPythonPath(Arrays.asList(egg), true, getRunConfiguration().getEnvs());
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

        PythonSdkFlavor<?> flavor = PythonSdkFlavor.getFlavor(getRunConfiguration().getSdk());
        if (flavor != null) {
          flavor.initPythonPath(Arrays.asList(egg), true, getRunConfiguration().getEnvs());
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
        waitForTerminate();
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
        return Collections.singleton("-iron");
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
        return Collections.singleton("-iron");
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
        var runningThread = myDebugProcess.getThreads().stream().filter(thread -> "Thread1".equals(thread.getName())).findFirst();
        assertNotNull(runningThread);
        setProcessCanTerminate(true);
        disposeDebugProcess();
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

      private static @Nullable String getRefWithWordInName(List<String> referrersNames, String word) {
        return ContainerUtil.find(referrersNames, x -> x.contains(word));
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
        return Collections.singleton("-iron");
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
        toggleBreakpoint(getScriptName(), 6);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        eval("i").hasValue("0");
        // remove break on line 2
        removeBreakpoint(getScriptName(), 6);
        resume();
        // add break on line 2
        toggleBreakpoint(getScriptName(), 6);
        // check if break on line 2 works
        waitForPause();
        // remove break on line 2 again
        removeBreakpoint(getScriptName(), 6);
        // add break on line 3
        toggleBreakpoint(getScriptName(), 7);
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
        return ImmutableSet.of("-iron", "-python3.8", "-python3.9", "-python3.10", "-python3.11", "-python3.12"); // PY-38604
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
        toggleBreakpoint(getFilePath("test1.py"), 7);
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
  public void testTypeHandler() {
    runPythonTest(new PyDebuggerTaskTagAware("/debug", "test_type_handler.py") {


      @Override
      public void before() {
        toggleBreakpoint(getFilePath(getScriptName()), 11);
      }

      @Override
      public boolean isLanguageLevelSupported(@NotNull final LanguageLevel level) {
        return level.compareTo(LanguageLevel.PYTHON27) > 0;
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
        eval("s10").hasValue("'❤'");
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
        // plus the "Protected Attributes" group.
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
        for (int i = 600; i < 600 + MAX_ITEMS_TO_HANDLE; i++) {
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

        assertEquals(effectiveMaxItemsNumber, children.size());
        for (int i = 0; i < MAX_ITEMS_TO_HANDLE; i++) {
          assertTrue(hasChildWithName(children, i));
          assertTrue(hasChildWithValue(children, i));
        }

        D.setOffset(600);
        children = loadVariable(D);
        assertEquals(effectiveMaxItemsNumber, children.size());
        for (int i = 600; i < 600 + MAX_ITEMS_TO_HANDLE; i++) {
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

        assertEquals(effectiveMaxItemsNumber, children.size());
        for (int i = 0; i < MAX_ITEMS_TO_HANDLE; i++) {
          assertTrue(hasChildWithValue(children, i));
        }

        S.setOffset(600);
        children = loadVariable(S);
        assertEquals(effectiveMaxItemsNumber, children.size());
        for (int i = 600; i < 600 + MAX_ITEMS_TO_HANDLE; i++) {
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

        assertEquals(effectiveMaxItemsNumber + 1, children.size()); // one extra child for maxlen
        for (int i = 1; i < MAX_ITEMS_TO_HANDLE; i++) {
          assertTrue(hasChildWithName(children, formatStr(i, collectionLength)));
          assertTrue(hasChildWithValue(children, i));
        }

        dq.setOffset(600);
        children = loadVariable(dq);
        assertEquals(effectiveMaxItemsNumber, children.size());
        for (int i = 600; i < 600 + MAX_ITEMS_TO_HANDLE; i++) {
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
        return Collections.singleton("pandas");
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

        PyDebugValue array = (PyDebugValue)children.getValue(5);

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
        PyDebugValue values = (PyDebugValue)children.getValue(children.size() - 1);
        children = loadVariable(values);
        array = (PyDebugValue)children.getValue(children.size() - 1);
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
        values = (PyDebugValue)children.getValue(children.size() - 1);
        children = loadVariable(values);
        array = (PyDebugValue)children.getValue(children.size() - 1);
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
        return Collections.singleton("-iron"); // PY-37793
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

      @Override
      public @NotNull Set<String> getTags() {
        return ImmutableSet.of("-python3.11", "-python3.12", "-python2.7"); // PY-59675, PY-59951
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
        return Collections.singleton("-iron"); // PY-37791
      }
    });
  }

  @Test
  public void testExecAndSpawnWithBytesArgs() {

    Assume.assumeFalse("Don't run under Windows", UsefulTestCase.IS_UNDER_TEAMCITY && SystemInfo.isWindows);

    final class ExecAndSpawnWithBytesArgsTask extends PyDebuggerTaskTagAware {

      private final static String BYTES_ARGS_WARNING =
        "pydev debugger: bytes arguments were passed to a new process creation function. " + "Breakpoints may not work correctly.\n";

      private ExecAndSpawnWithBytesArgsTask(@Nullable String relativeTestDataPath, String scriptName) {
        super(relativeTestDataPath, scriptName);
      }

      @Override
      protected void init() {
        setMultiprocessDebug(true);
      }

      @Override
      public boolean isLanguageLevelSupported(@NotNull final LanguageLevel level) {
        return level.compareTo(LanguageLevel.PYTHON27) > 0;
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
        waitForOutput(BYTES_ARGS_WARNING);
      }

    }

    Arrays.asList("test_call_exec_with_bytes_args.py", "test_call_spawn_with_bytes_args.py")
      .forEach((script) -> runPythonTest(new ExecAndSpawnWithBytesArgsTask("/debug", script)));
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
        assertTrue(findDebugValueByName(frameVariables, ".0").getType().endsWith("_iterator"));
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
        return Collections.singleton("python3");
      }
    });
  }

  @Test
  public void testCallingSettraceWarning() {
    var warning = "PYDEV DEBUGGER WARNING:\nsys.settrace() should not be used when the debugger is being used.";
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
        if (!stderr().contains(warning)) {
          outputContains(warning);
        }
      }

      @NotNull
      @Override
      public Set<String> getTags() {
        return Collections.singleton("-iron"); // PY-37796
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
        return Collections.singleton("-iron"); // PY-36367
      }
    });
  }

  @Test
  public void testCodeEvaluationWithGeneratorExpression() {
    runPythonTest(new PyDebuggerTaskTagAware("/debug", "test_code_eval_with_generator_expr.py") {

      @Override
      public void before() {
        toggleBreakpoint(getFilePath(getScriptName()), 8);
      }

      @Override
      public void testing() throws Exception {
        String[] expectedOutput = ("""
                                     [True] \t [True]
                                     [False] \t [False]
                                     [None] \t [None]""").split("\n");
        waitForPause();
        for (String line : expectedOutput) {
          waitForOutput(line);
        }
        consoleExec("""
                      TFN = [True, False, None]
                      for q in TFN:
                          gen = (c for c in TFN if c == q)
                          lcomp = [c for c in TFN if c == q]
                          print(list(gen), "\\t", list(lcomp))""");
          for (String line : expectedOutput) {
            waitForOutput(line, 2);
          }
        consoleExec("""
                      def g():
                          print("Foo, bar, baz")
                      def f():
                          g()
                      f()""");
        waitForOutput("Foo, bar, baz");
        resume();
        waitForTerminate();
      }

      @Override
      public boolean isLanguageLevelSupported(@NotNull final LanguageLevel level) {
        return level.compareTo(LanguageLevel.PYTHON27) > 0;
      }
    });
  }

  @Test
  public void testCodeEvaluationWithPandas() {
    runPythonTest(new PyDebuggerTask("/debug", "test_dataframe.py") {
      @Override
      public void before() {
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
        return Collections.singleton("pandas");
      }
    });
  }

  @Test
  public void testNoDebuggerRelatedStacktraceOnDebuggerStop() {
    runPythonTest(new PyDebuggerTask("/debug", "test1.py") {
      @Override
      public void before() {
        toggleBreakpoint(getFilePath(getScriptName()), 6);
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

      @Override
      public boolean isLanguageLevelSupported(@NotNull final LanguageLevel level) {
        return level.compareTo(LanguageLevel.PYTHON38) != 0;
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
        return Collections.singleton("pandas");
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

  @Test
  public void testDontStopTwiceOnException() {
    runPythonTest(new PyDebuggerTask("/debug", "test_double_stop_on_exception.py") {
      @Override
      public void before() {
        toggleBreakpoint(getFilePath(getScriptName()), 3);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        // Check: debugger doesn't stop on the breakpoint second time
        resume();
        waitForTerminate();
      }
    });
  }

  @Test
  public void testLoadElementsForGroupsOnDemand() {
    runPythonTest(new PyDebuggerTask("/debug", "test_load_elements_for_groups_on_demand.py") {
      @Override
      public void before() {
        toggleBreakpoint(getFilePath(getScriptName()), 2);
        toggleBreakpoint(getFilePath(getScriptName()), 8);
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
        resume();
        waitForPause();

        List<PyDebugValue> defaultVariables = loadSpecialVariables(ProcessDebugger.GROUP_TYPE.DEFAULT);
        List<String> names = List.of("_dummy_ret_val", "_dummy_special_var", "boolean", "get_foo", "string");
        List<String> values = List.of("", "True", "1", "Hello!");
        containsValue(defaultVariables, names, values);

        List<PyDebugValue> specialVariables = loadSpecialVariables(ProcessDebugger.GROUP_TYPE.SPECIAL);
        names = List.of("__builtins__", "__doc__", "__file__", "__loader__", "__name__", "__package__", "__spec__");
        values = List.of("<module 'builtins' (built-in)>", "None", "test_load_elements_for_groups_on_demand.py", " ", "__main__", "");
        containsValue(specialVariables, names, values);

        List<PyDebugValue> returnVariables = loadSpecialVariables(ProcessDebugger.GROUP_TYPE.RETURN);
        names = List.of("foo");
        values = List.of("1");
        containsValue(returnVariables, names, values);

        resume();
        waitForTerminate();
      }

      private static void containsValue(List<PyDebugValue> variablesGroup, List<String> names, List<String> values) {
        for (PyDebugValue elem : variablesGroup) {
          assertTrue(names.contains(elem.getName()));
          assertTrue(values.contains(elem.getValue()));
        }
      }
    });
  }

  @Test
  public void testDictWithUnicodeOrBytesValuesOrNames() {
    runPythonTest(new PyDebuggerTaskTagAware("/debug", "test_dict_with_unicode_or_bytes_values_names.py") {

      @Override
      public void testing() throws Exception {
        waitForOutput("{\"u'Foo “Foo” Bar' (4706573888)\": '“Foo”'}");
        waitForOutput("{b'\\xfc\\x00': b'\\x00\\x10'}");
      }

      @Override
      public boolean isLanguageLevelSupported(@NotNull final LanguageLevel level) {
        return level.compareTo(LanguageLevel.PYTHON27) > 0;
      }
    });
  }

  @Test
  public void testStringRepresentationInVariablesView() {
    runPythonTest(new PyDebuggerTask("/debug", "test_string_representation_in_variables_view.py") {
      @Override
      public void before() {
        toggleBreakpoint(getFilePath(getScriptName()), 17);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        List<PyDebugValue> frameVariables = loadFrame();
        checkVariableValue(frameVariables, "str", "foo_str");
        checkVariableValue(frameVariables, "repr", "foo_repr");
        String expected = eval("repr(foo_reprlib)").getValue().replaceAll("[\"']", "");
        checkVariableValue(frameVariables, expected, "foo_reprlib");
        resume();
        waitForTerminate();
      }

      private void checkVariableValue(List<PyDebugValue> frameVariables, String expected, String name)
        throws PyDebuggerException, InterruptedException {
        PyDebugValue value = findDebugValueByName(frameVariables, name);
        loadVariable(value);
        synchronized (this) {
          while (value.getValue().isEmpty() || value.getValue().isBlank()) {
            wait(1000);
          }
        }
        assertEquals(expected, value.getValue());
      }
    });
  }

  @Test
  public void testGetFullValueFromCopyAction() {
    runPythonTest(new PyDebuggerTask("/debug", "test_get_full_value_from_copy_action.py") {

      private static final int MINIMAL_LENGTH = 10000;

      private void testLength(String value) throws PyDebuggerException {
        // PyXCopyAction uses PyFullValueEvaluator, it uses myDebugProcess.evaluate
        PyDebugValue result = myDebugProcess.evaluate(value, false, false);
        assertTrue(result.getValue().length() > MINIMAL_LENGTH);
      }
      @Override
      public void before() {
        toggleBreakpoint(getFilePath(getScriptName()), 5);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        testLength("lst");
        resume();
      }
    });
  }

  @Test
  public void testQuotingInCopyAction() {
    runPythonTest(new PyDebuggerTask("/debug", "test_quoting_value.py") {
      private void testQuotingValue(String value) throws PyDebuggerException {
        var variable = myDebugProcess.evaluate(value, false, false).getValue();
        for (var policy : QuotingPolicy.values()) {
          String result = getQuotingString(policy, variable);
          switch (policy) {
            case DOUBLE -> assertFalse(result.contains("'"));
            case SINGLE -> assertFalse(result.contains("\""));
            case NONE -> {
              assertFalse(result.contains("'"));
              assertFalse(result.contains("\""));
            }
          }
        }
      }

      @Override
      public void before() {
        toggleBreakpoint(getFilePath(getScriptName()), 10);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        testQuotingValue("car");
        testQuotingValue("some_str");
        testQuotingValue("some_lst");
        testQuotingValue("some_dict");
        resume();
      }
    });
  }

  private static class PyDebuggerTaskTagAware extends PyDebuggerTask {

    private PyDebuggerTaskTagAware(@Nullable String relativeTestDataPath, String scriptName) {
      super(relativeTestDataPath, scriptName);
    }

    public boolean hasTag(String tag) throws NullPointerException {
      String env = Paths.get(myRunConfiguration.getSdkHome()).getParent().getParent().toString();
      return ContainerUtil.exists(envTags.get(env), (t) -> t.startsWith(tag));
    }
  }
}
