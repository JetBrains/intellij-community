// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.env.debug;

import com.intellij.idea.TestFor;
import com.intellij.openapi.util.SystemInfo;
import com.jetbrains.env.EnvTestTagsRequired;
import com.jetbrains.env.PyEnvTestCase;
import org.jetbrains.annotations.Nullable;
import org.junit.Assume;
import org.junit.Test;

import java.util.HashSet;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PythonDebuggerMultiprocessingTest extends PyEnvTestCase {

  private static class PyDebuggerMultiprocessTask extends PyDebuggerTask {

    PyDebuggerMultiprocessTask(@Nullable String relativeTestDataPath, String scriptName) {
      super(relativeTestDataPath, scriptName);
    }

    @Override
    protected void init() {
      setMultiprocessDebug(true);
    }
  }

  @EnvTestTagsRequired(tags = "python3")
  @Test
  public void testMultiprocess() {
    runPythonTest(new PyDebuggerMultiprocessTask("/debug", "test_multiprocess.py") {
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
    });
  }

  @Test
  public void testMultiprocessingSubprocess() {
    runPythonTest(new PyDebuggerMultiprocessTask("/debug", "test_multiprocess_args.py") {
      @Override
      public void before() {
        toggleBreakpoint(getFilePath("test_multiprocess_args_child.py"), 4);
        setWaitForTermination(false);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        eval("import sys");
        eval("sys.argv[1]").hasValue("'subprocess'");
        eval("sys.argv[2]").hasValue("'etc etc'");

        resume();
      }
    });
  }

  @EnvTestTagsRequired(tags = "-iron")
  @Test
  public void testMultiprocessPool() {
    runPythonTest(new PyDebuggerMultiprocessTask("/debug", "test_multiprocess_pool.py") {
      @Override
      public void testing() throws Exception {
        waitForOutput("Done");
        waitForTerminate();
        assertFalse(output().contains("KeyboardInterrupt"));
      }
    });
  }

  @Test
  public void testPythonSubprocessWithCParameter() {
    runPythonTest(new PyDebuggerMultiprocessTask("/debug", "test_python_subprocess_with_c_parameter.py") {
      @Override
      public void before() {
        toggleBreakpoint(getFilePath("test_python_subprocess_another_helper.py"), 2);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        eval("x").hasValue("42");
        resume();
        waitForOutput("Hello!");
      }

    });
  }

  @Test
  public void testSubprocess() {
    runPythonTest(new PyDebuggerTask("/debug", "test_subprocess.py") {
      @Override
      public void before() {
        toggleBreakpoint(getFilePath(getScriptName()), 8);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        resume();
        waitForOutput("The subprocess finished with the return code 0.");
        waitForTerminate();
      }
    });
  }

  @Test
  public void testSubprocessModule() {
    runPythonTest(new PyDebuggerMultiprocessTask("/debug", "test_subprocess_module.py") {
      @Override
      public void before() {
        toggleBreakpoint(getFilePath("test_python_subprocess_another_helper.py"), 2);
        setWaitForTermination(false);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        eval("x").hasValue("42");
        resume();
        waitForOutput("Module returned code 0");
      }
    });
  }

  @Test
  public void testMultiprocessProcess() {
    runPythonTest(new PyDebuggerMultiprocessTask("/debug", "test_multiprocess_process.py") {
      @Override
      public void before() {
        toggleBreakpoint(getFilePath("test_multiprocess_process.py"), 5);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        eval("name").hasValue("'subprocess'");
        resume();
        waitForTerminate();
      }
    });
  }

  @EnvTestTagsRequired(tags = "python3.8")
  @Test
  public void testPosixSpawn() {

    Assume.assumeFalse("Windows doesn't support `posix_spawn`", SystemInfo.isWindows);

    runPythonTest(new PyDebuggerMultiprocessTask("/debug", "test_posix_spawn.py") {
      @Override
      public void before() {
        toggleBreakpoint(getFilePath(getScriptName()), 6);
        toggleBreakpoint(getFilePath("test2.py"), 7);
        setWaitForTermination(false);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        eval("z").hasValue("2");
        resume();
        waitForPause();
        resume();
        waitForOutput("Process finished with exit code 0");
      }
    });
  }

  @EnvTestTagsRequired(tags = "python3")
  @Test
  public void testSubprocessIsolated() {
    runPythonTest(new PyDebuggerMultiprocessTask("/debug", "test_subprocess_isolated.py") {
      @Override
      public void before() {
        toggleBreakpoint(getFilePath("test_python_subprocess_another_helper.py"), 2);
        setWaitForTermination(false);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        eval("x").hasValue("42");
        resume();
        waitForOutput("Module returned code 0");
      }
    });
  }

  @EnvTestTagsRequired(tags = "python2.7")
  @Test
  public void testCallExecWithPythonArg() {
    runPythonTest(new PyDebuggerMultiprocessTask("/debug", "test_call_exec_with_python_arg.py") {
      @Override
      public void before() {
        toggleBreakpoint(getFilePath("test4.py"), 1);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        setProcessCanTerminate(true);
        resume();
        waitForOutput("3");
      }
    });

    runPythonTest(new PyDebuggerTask("/debug", "test_call_python_version.py") {
      @Override
      public void testing() throws Exception {
        waitForTerminate();
        outputContains("Python");
      }
    });
  }

  @Test
  @TestFor(issues = "PY-37366")
  @EnvTestTagsRequired(tags = "python3")
  public void testMultiprocessManagerFork() {
    runPythonTest(new PyDebuggerMultiprocessTask("/debug", "test_multiprocess_manager_fork.py") {
      @Override
      public void before() {
        toggleBreakpoint(getScriptName(), 5);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        resume();
        waitForTerminate();
        waitForOutput("Process finished with exit code 0");
      }
    });
  }

  @Test
  @TestFor(issues = "PY-65353")
  public void testDebuggerStopsOnBreakpointInEveryProcess() {
    runPythonTest(new PyDebuggerMultiprocessTask("/debug", "test_multiprocess_2.py") {
      @Override
      public void before() {
        toggleBreakpoint(5);
      }

      @Override
      public void testing() throws Exception {
        var expectedValues = new HashSet<String>();
        for (int i = 1; i < 4; i++) {
          expectedValues.add(Integer.toString(i));
        }

        waitForPause();
        var first = eval("x").getValue();
        assertTrue(expectedValues.contains(first));
        expectedValues.remove(first);
        resume();

        waitForPause();
        var second = eval("x").getValue();
        assertTrue(expectedValues.contains(second));
        expectedValues.remove(second);
        resume();

        waitForPause();
        var third = eval("x").getValue();
        assertTrue(expectedValues.contains(third));
        resume();

        waitForTerminate();
      }
    });
  }

  @EnvTestTagsRequired(tags = "joblib")
  @Test
  @TestFor(issues = "PY-36882")
  public void testNoErrorMessagesWithJoblib() {
    runPythonTest(new PyDebuggerMultiprocessTask("/debug", "test_joblib.py") {
      @Override
      public void before() {
        toggleBreakpoint(4);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        resume();
        waitForPause();
        resume();
        waitForPause();
        resume();
        waitForTerminate();
        assertFalse(output().contains("Traceback"));
      }
    });
  }
}
