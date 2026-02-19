// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.debug.tests;

import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.testFramework.UsefulTestCase;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.debug.tasks.PyDebuggerTask;
import com.jetbrains.python.debugger.PyDebuggerOptionsProvider;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assume;
import org.junit.Test;

import java.util.Set;

public class PythonDebuggerQtTest extends PyEnvTestCase {

  private static class PyDebuggerQtTask extends PyDebuggerTask {

    PyDebuggerQtTask(@Nullable String relativeTestDataPath, String scriptName) {
      super(relativeTestDataPath, scriptName);
    }

    @NotNull
    @Override
    public Set<String> getTags() {
      return ImmutableSet.of("qt");
    }

    @Override
    public boolean isLanguageLevelSupported(@NotNull final LanguageLevel level) {
      return level.compareTo(LanguageLevel.PYTHON38) != 0;
    }
  }

  @Test
  public void testPyQtQThreadInheritor() {
    Assume.assumeFalse("Don't run under Windows", UsefulTestCase.IS_UNDER_TEAMCITY && SystemInfo.isWindows);

    runPythonTest(new PyDebuggerQtTask("/debug/qt", "test_pyqt1.py") {

      @Override
      public void before() {
        toggleBreakpoint(getScriptName(), 8);
        PyDebuggerOptionsProvider.getInstance(myFixture.getProject()).setPyQtBackend("pyqt5");
      }

      @Override
      public void doFinally() {
        PyDebuggerOptionsProvider.getInstance(myFixture.getProject()).setPyQtBackend("auto");
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
    });
  }

  @Test
  public void testPyQtMoveToThread() {
    Assume.assumeFalse("Don't run under Windows", UsefulTestCase.IS_UNDER_TEAMCITY && SystemInfo.isWindows);

    runPythonTest(new PyDebuggerQtTask("/debug/qt", "test_pyqt2.py") {

      @Override
      public void before() {
        toggleBreakpoint(getScriptName(), 10);
        PyDebuggerOptionsProvider.getInstance(myFixture.getProject()).setPyQtBackend("pyqt5");
      }

      @Override
      public void doFinally() {
        PyDebuggerOptionsProvider.getInstance(myFixture.getProject()).setPyQtBackend("auto");
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
    });
  }


  @Test
  public void testPyQtQRunnableInheritor() {
    Assume.assumeFalse("Don't run under Windows", UsefulTestCase.IS_UNDER_TEAMCITY && SystemInfo.isWindows);

    runPythonTest(new PyDebuggerQtTask("/debug/qt", "test_pyqt3.py") {

      @Override
      public void before() {
        toggleBreakpoint(getScriptName(), 9);
        PyDebuggerOptionsProvider.getInstance(myFixture.getProject()).setPyQtBackend("pyqt5");
      }

      @Override
      public void doFinally() {
        PyDebuggerOptionsProvider.getInstance(myFixture.getProject()).setPyQtBackend("auto");
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
    });
  }

  @Test
  public void testPyQtStopOnCrash() {
    Assume.assumeFalse("Don't run under Windows", UsefulTestCase.IS_UNDER_TEAMCITY && SystemInfo.isWindows);

    runPythonTest(new PyDebuggerQtTask("/debug/qt", "test_pyqt4.py") {
      @Override
      public void before() throws Exception {
        createExceptionBreak(myFixture, true, true, true);
        toggleBreakpoint(getFilePath(getScriptName()), 16);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        resume();
        waitForPause();
        eval("__exception__[0].__name__").hasValue("'NameError'");
        setProcessCanTerminate(true);
        resume();
        waitForPause();
        resume();
        waitForOutput("Process finished with exit code 1");
      }
    });
  }

  @Test
  public void testPyQtCrashTracebackIsShownForConditionalException() {
    Assume.assumeFalse("Don't run under Windows", UsefulTestCase.IS_UNDER_TEAMCITY && SystemInfo.isWindows);

    runPythonTest(new PyDebuggerQtTask("/debug/qt", "test_pyqt4.py") {
      @Override
      public void before() throws Exception {
        createExceptionBreak(myFixture, true, true, true, "2 + 2 == 5",
                             null);
        toggleBreakpoint(getFilePath(getScriptName()), 16);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        setProcessCanTerminate(true);
        resume();
        waitForOutput("NameError: name 'wrong_arg' is not defined");
        waitForOutput("Process finished with exit code 1");
      }
    });
  }

  @Test
  public void testPySide2QThreadInheritor() {
    Assume.assumeFalse("Don't run under Windows", UsefulTestCase.IS_UNDER_TEAMCITY && SystemInfo.isWindows);

    runPythonTest(new PyDebuggerQtTask("/debug/qt", "test_pyside2_1.py") {

      @Override
      public void before() {
        toggleBreakpoint(getScriptName(), 8);
        PyDebuggerOptionsProvider.getInstance(myFixture.getProject()).setPyQtBackend("pyside2");
      }

      @Override
      public void doFinally() {
        PyDebuggerOptionsProvider.getInstance(myFixture.getProject()).setPyQtBackend("auto");
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
    });

  }

  @Test
  public void testPySide2MoveToThread() {
    Assume.assumeFalse("Don't run under Windows", UsefulTestCase.IS_UNDER_TEAMCITY && SystemInfo.isWindows);

    runPythonTest(new PyDebuggerQtTask("/debug/qt", "test_pyside2_2.py") {

      @Override
      public void before() {
        toggleBreakpoint(getScriptName(), 10);
        PyDebuggerOptionsProvider.getInstance(myFixture.getProject()).setPyQtBackend("pyside2");
      }

      @Override
      public void doFinally() {
        PyDebuggerOptionsProvider.getInstance(myFixture.getProject()).setPyQtBackend("auto");
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
    });
  }

  @Test
  public void testPySide2QRunnableInheritor() {
    Assume.assumeFalse("Don't run under Windows", UsefulTestCase.IS_UNDER_TEAMCITY && SystemInfo.isWindows);

    runPythonTest(new PyDebuggerQtTask("/debug/qt", "test_pyside2_3.py") {

      @Override
      public void before() {
        toggleBreakpoint(getScriptName(), 9);
        PyDebuggerOptionsProvider.getInstance(myFixture.getProject()).setPyQtBackend("pyside2");
      }

      @Override
      public void doFinally() {
        PyDebuggerOptionsProvider.getInstance(myFixture.getProject()).setPyQtBackend("auto");
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
    });
  }
}
