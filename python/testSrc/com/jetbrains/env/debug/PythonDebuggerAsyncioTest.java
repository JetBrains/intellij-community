// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.debug;

import com.intellij.xdebugger.XDebuggerTestUtil;
import com.jetbrains.env.EnvTestTagsRequired;
import com.jetbrains.env.PyEnvTestCase;
import org.junit.Test;

public class PythonDebuggerAsyncioTest extends PyEnvTestCase {

  private enum TestCase {
    CONSOLE,
    EVALUATE,
    BREAKPOINT
  }

  private static class AsyncioPyDebuggerTask extends PyDebuggerTask {
    private static final String RELATIVE_PATH = "/debug";

    private static final String SCRIPT_NAME = "test_asyncio_debugger.py";

    private static final String AWAIT_FOO = "await foo(1)";

    private static final String RUN_FOO = "asyncio.run(foo(1))";

    private static final String RUN_FOO_WITH_LOOP = "loop.run_until_complete(foo(1))";

    private static final String GET_EVENT_LOOP = "loop = asyncio.get_event_loop()";

    private static final String CLOSE_EVENT_LOOP = "loop.close()";

    private static final String RUN_UNTIL_COMPLETE = "asyncio.get_event_loop().run_until_complete(foo(1))";

    private final TestCase myTestCase;

    private AsyncioPyDebuggerTask(TestCase testCase) {
      super(RELATIVE_PATH, SCRIPT_NAME);
      myTestCase = testCase;
    }

    protected void testConsole() throws Exception {
      consoleExec(AWAIT_FOO);
      waitForOutput("2");
      consoleExec(RUN_FOO);
      waitForOutput("2");
      consoleExec(GET_EVENT_LOOP);
      consoleExec(RUN_FOO_WITH_LOOP);
      waitForOutput("2");
      consoleExec(CLOSE_EVENT_LOOP);
    }

    protected void testEvaluate() {
      Variable await_foo = eval(AWAIT_FOO);
      await_foo.hasValue("2");
      Variable run_foo = eval(RUN_FOO);
      run_foo.hasValue("2");
      Variable loop_foo = eval(RUN_UNTIL_COMPLETE);
      loop_foo.hasValue("2");
    }

    protected void testBreakpoints() throws Exception {
      resume();
      waitForOutput("2");
      waitForTerminate();
    }

    @Override
    public void testing() throws Exception {
      waitForPause();
      switch (myTestCase) {
        case CONSOLE -> testConsole();
        case EVALUATE -> testEvaluate();
        case BREAKPOINT -> testBreakpoints();
      }
    }

    @Override
    public void before() {
      switch (myTestCase) {
        case CONSOLE, EVALUATE -> toggleBreakpoint(9);
        case BREAKPOINT -> {
          toggleBreakpoint(8);
          toggleBreakpoint(9);
          XDebuggerTestUtil.setBreakpointCondition(getProject(), 8, "await foo(1) == 2");
          XDebuggerTestUtil.setBreakpointCondition(getProject(), 9, "await foo(1) != 2");
          XDebuggerTestUtil.setBreakpointLogExpression(getProject(), 8, "await foo(1)");
        }
      }
      setWaitForTermination(false);
    }
  }

  @EnvTestTagsRequired(tags = "python3.8")
  @Test
  public void testAsyncioConsole38() {
    runPythonTest(new AsyncioPyDebuggerTask(TestCase.CONSOLE));
  }

  @EnvTestTagsRequired(tags = "python3.9")
  @Test
  public void testAsyncioConsole39() {
    runPythonTest(new AsyncioPyDebuggerTask(TestCase.CONSOLE));
  }

  @EnvTestTagsRequired(tags = "python3.10")
  @Test
  public void testAsyncioConsole310() {
    runPythonTest(new AsyncioPyDebuggerTask(TestCase.CONSOLE));
  }


  @EnvTestTagsRequired(tags = "python3.8")
  @Test
  public void testAsyncioEvaluate38() {
    runPythonTest(new AsyncioPyDebuggerTask(TestCase.EVALUATE));
  }

  @EnvTestTagsRequired(tags = "python3.9")
  @Test
  public void testAsyncioEvaluate39() {
    runPythonTest(new AsyncioPyDebuggerTask(TestCase.EVALUATE));
  }

  @EnvTestTagsRequired(tags = "python3.10")
  @Test
  public void testAsyncioEvaluate310() {
    runPythonTest(new AsyncioPyDebuggerTask(TestCase.EVALUATE));
  }

  @EnvTestTagsRequired(tags = "python3.8")
  @Test
  public void testAsyncioBreakpoint38() {
    runPythonTest(new AsyncioPyDebuggerTask(TestCase.BREAKPOINT));
  }

  @EnvTestTagsRequired(tags = "python3.9")
  @Test
  public void testAsyncioBreakpoint39() {
    runPythonTest(new AsyncioPyDebuggerTask(TestCase.BREAKPOINT));
  }

  @EnvTestTagsRequired(tags = "python3.10")
  @Test
  public void testAsyncioBreakpoint310() {
    runPythonTest(new AsyncioPyDebuggerTask(TestCase.BREAKPOINT));
  }
}
