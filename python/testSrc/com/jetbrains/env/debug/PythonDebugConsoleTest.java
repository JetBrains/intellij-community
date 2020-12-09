// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.env.debug;

import com.google.common.collect.ImmutableSet;
import com.jetbrains.env.PyEnvTestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.Set;

public class PythonDebugConsoleTest extends PyEnvTestCase {

  @Test
  public void testDebugConsoleExecAndOutput() {
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

  // PY-38378
  @Test
  public void testUpdateVariableInDebugConsole() {
    runPythonTest(new PyDebuggerTask("/debug", "test2.py") {
      @Override
      public void before() {
        toggleBreakpoints(5, 7);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        consoleExecAndWait("z = 42");
        resume();
        waitForPause();
        eval("z").hasValue("43");
        resume();
        waitForTerminate();
      }
    });
  }

  // PY-38424
  @Test
  public void testUpdateSeriesInDebugConsole() {
    runPythonTest(new PyDebuggerTask("/debug", "test_update_series_in_debug_console.py") {
      @Override
      public void before() throws Exception {
        toggleBreakpoint(11);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        eval("str(a[40])").hasValue("'1'");
        consoleExecAndWait("a[40] = 100");
        eval("str(a[40])").hasValue("'100'");
        consoleExecAndWait("        def g():\n" +
                    "            return pd.Series(index=[40,50,60], data=[1,2,3])");
        consoleExecAndWait("a = g()");
        eval("str(a[40])").hasValue("'1'");
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
}
