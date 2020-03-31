// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.env.python.debug;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.intellij.openapi.util.SystemInfo;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.Staging;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assume;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertFalse;

@Staging
public class PythonDebuggerMultiprocessingTest extends PyEnvTestCase {

  private static class PyDebuggerMultiprocessTask extends PyDebuggerTask {

    public PyDebuggerMultiprocessTask(@Nullable String relativeTestDataPath, String scriptName) {
      super(relativeTestDataPath, scriptName);
    }

    @Override
    protected void init() {
      setMultiprocessDebug(true);
    }
  }

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

      @NotNull
      @Override
      public Set<String> getTags() {
        return Sets.newHashSet("python3");
      }
    });
  }

  @Test
  public void testMultiprocessingSubprocess() {
    runPythonTest(new PyDebuggerMultiprocessTask("/debug", "test_multiprocess_args.py") {
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
  public void testMultiprocessPool() {
    runPythonTest(new PyDebuggerMultiprocessTask("/debug", "test_multiprocess_pool.py") {
      @Override
      public void testing() throws Exception {
        waitForOutput("Done");
        assertFalse(output().contains("KeyboardInterrupt"));
      }

      @NotNull
      @Override
      public Set<String> getTags() {
        return ImmutableSet.of("-iron");
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

      @NotNull
      @Override
      public Set<String> getTags() {
        return ImmutableSet.of("-iron", "-jython");
      }
    });
  }

  @Test
  public void testMultiprocessProcess() {
    runPythonTest(new PyDebuggerMultiprocessTask("/debug", "test_multiprocess_process.py") {
      @Override
      public void before() {
        toggleBreakpoint(getFilePath("test_multiprocess_process.py"), 5);
        setWaitForTermination(false);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        eval("name").hasValue("'subprocess'");
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
  public void testSubprocess() {
    runPythonTest(new PyDebuggerTask("/debug", "test_subprocess.py") {
      @Override
      public void before() throws Exception {
        toggleBreakpoint(getFilePath(getScriptName()), 8);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        resume();
        waitForTerminate();
        outputContains("The subprocess finished with the return code 0.");
      }
    });
  }

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

      @NotNull
      @Override
      public Set<String> getTags() {
        return ImmutableSet.of("python3.8");
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
}
