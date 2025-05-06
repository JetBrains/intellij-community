// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.debug.tests;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XDebuggerTestUtil;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.debug.tasks.PyDebuggerTask;
import com.jetbrains.python.console.pydev.PydevCompletionVariant;
import com.jetbrains.python.debugger.*;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.run.PythonRunConfiguration;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assume;
import org.junit.Test;

import java.util.*;

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

  private static void createDefaultExceptionBreakpoint(IdeaProjectTestFixture fixture) {
    XDebuggerTestUtil.removeAllBreakpoints(fixture.getProject());
    XDebuggerTestUtil.setDefaultBreakpointEnabled(fixture.getProject(), PyExceptionBreakpointType.class, true);
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

      @Override
      public boolean isLanguageLevelSupported(@NotNull final LanguageLevel level) {
        return level.compareTo(LanguageLevel.PYTHON27) > 0;
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
        if (getRefWithWordInName(referrersNames, "dict") == null) {
          assertNotNull(getRefWithWordInName(referrersNames, "tuple"));
        }

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

      @Override
      public boolean isLanguageLevelSupported(@NotNull final LanguageLevel level) {
        return level.compareTo(LanguageLevel.PYTHON38) < 0; // PY-38604
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

      @Override
      public boolean isLanguageLevelSupported(@NotNull final LanguageLevel level) {
        return level.compareTo(LanguageLevel.PYTHON38) < 0;
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

      @Override
      public boolean isLanguageLevelSupported(@NotNull final LanguageLevel level) {
        return level.compareTo(LanguageLevel.PYTHON27) > 0;
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
      protected AbstractPythonRunConfiguration createRunConfiguration(@NotNull String sdkHome, @Nullable Sdk existingSdk) {
        PythonRunConfiguration runConfiguration = (PythonRunConfiguration)super.createRunConfiguration(sdkHome, existingSdk);
        runConfiguration.getEnvs().put("PYDEVD_USE_CYTHON", "NO");
        return runConfiguration;
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
    });
  }

  @Test
  public void testCodeEvaluationWithGeneratorExpression() {
    runPythonTest(new PyDebuggerTask("/debug", "test_code_eval_with_generator_expr.py") {

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
        waitForTerminate();
        assertFalse("Output shouldn't contain debugger related stacktrace when debugger is stopped",
                    output().contains("pydevd.py\", line "));
      }

      @Override
      public boolean isLanguageLevelSupported(@NotNull final LanguageLevel level) {
        return level.compareTo(LanguageLevel.PYTHON38) > 0;
      }

      @Override
      protected AbstractPythonRunConfiguration createRunConfiguration(@NotNull String sdkHome, @Nullable Sdk existingSdk) {
        PythonRunConfiguration runConfiguration = (PythonRunConfiguration)super.createRunConfiguration(sdkHome, existingSdk);
        runConfiguration.getEnvs().put("PYDEVD_USE_CYTHON", "NO");
        return runConfiguration;
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
  public void testDictWithUnicodeOrBytesValuesOrNames() {
    runPythonTest(new PyDebuggerTask("/debug", "test_dict_with_unicode_or_bytes_values_names.py") {

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
}
