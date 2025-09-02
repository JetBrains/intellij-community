// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.debug.tests;

import com.google.common.collect.ImmutableSet;
import com.intellij.testFramework.UsefulTestCase;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.PyTestTask;
import com.jetbrains.env.debug.tasks.PyTestDebuggingTask;
import com.jetbrains.env.debug.tasks.PyUnitTestDebuggingTask;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.util.Collections;
import java.util.Set;

public class PythonUnitTestsDebuggingTest extends PyEnvTestCase {
  @Override
  public void runPythonTest(PyTestTask testTask) {
    // Don't run on TeamCity because of PY-45432.
    Assume.assumeFalse(UsefulTestCase.IS_UNDER_TEAMCITY);
    super.runPythonTest(testTask);
  }

  @Test
  public void testPythonExceptionDataClass() {
    PyUnitTestDebuggingTask.PythonExceptionData pythonExceptionModel = PyUnitTestDebuggingTask.PythonExceptionData.fromString(
      "(<class 'foo'>, AssertionError('bar is not baz'), <traceback object at 0x123456789>)");
    Assert.assertEquals("foo", pythonExceptionModel.getExceptionClass());
    Assert.assertEquals("bar is not baz", pythonExceptionModel.getErrorMessage());
    Assert.assertNull(PyUnitTestDebuggingTask.PythonExceptionData.fromString("foobar"));
  }

  @Test
  public void testUnitTestSubTest() {
    runPythonTest(new PyUnitTestDebuggingTask("test_case_1.py", "MyTestCase") {
      @Override
      public void testing() throws Exception {
        waitForPauseOnTestFailure("AssertionError", "1 != 0");
        resume();
        waitForPauseOnTestFailure("AssertionError", "1 != 0");
        resume();
        waitForPauseOnTestFailure("AssertionError", "1 != 0");
      }

      @Override
      public @NotNull Set<String> getTags() {
        //noinspection SpellCheckingInspection
        return ImmutableSet.of("pytest", "python3");
      }
    });
  }

  @Test
  public void testUnitTestFalseIsNotTrue() {
    runPythonTest(new PyUnitTestDebuggingTask("test_case_2.py", "MyTestCase") {
      @Override
      public void testing() throws Exception {
        waitForPauseOnTestFailure("AssertionError", "False is not true");
      }
    });
  }

  @Test
  public void testPyTestFalseIsNotTrue() {
    runPythonTest(new PyTestDebuggingTask("test_case_2.py", "MyTestCase") {
      @Override
      public void testing() throws Exception {
        waitForPauseOnTestFailure("AssertionError", "False is not true");
      }
    });
  }

  @Test
  public void testPyTestTwoPlusTwoIsFive() {
    runPythonTest(new PyTestDebuggingTask("test_case_3.py") {
      @Override
      public void testing() throws Exception {
        waitForPauseOnTestFailure("AssertionError", "assert 4 == 5");
      }
    });
  }

  @Test
  public void testExceptionInFixtureDecoratorPython3() {
    runPythonTest(new PyTestDebuggingTask("test_case_4.py") {
      @Override
      public void testing() throws Exception {
        waitForPauseOnTestFailure("ZeroDivisionError", "division by zero");
      }

      @Override
      public @NotNull Set<String> getTags() {
        //noinspection SpellCheckingInspection
        return ImmutableSet.of("pytest", "python3");
      }
    });
  }

  @Test
  public void testExceptionInFixtureDecoratorPython2() {
    runPythonTest(new PyTestDebuggingTask("test_case_4.py") {
      @Override
      public void testing() throws Exception {
        waitForPauseOnTestFailure("ZeroDivisionError", "integer division or modulo by zero");
      }

      @Override
      public @NotNull Set<String> getTags() {
        //noinspection SpellCheckingInspection
        return ImmutableSet.of("pytest", "python2.7");
      }
    });
  }

  @Test
  public void testDontStopOnMissingFixture() {
    runPythonTest(new PyTestDebuggingTask("test_case_5.py") {
      @Override
      public void testing() throws Exception {
        waitForTerminate();
      }
    });
  }

  @SuppressWarnings("SpellCheckingInspection")
  @Test
  public void testExceptionInFixtureFromConftest() {
    runPythonTest(new PyTestDebuggingTask("test_case_6.py") {
      @Override
      public void testing() throws Exception {
        waitForPauseOnTestFailure("RuntimeError", "Boom!");
      }
    });
  }

  @Test
  public void testPyTestExceptionInTearDown() {
    runPythonTest(new PyTestDebuggingTask("test_case_7.py") {
      @Override
      public void testing() throws Exception {
        waitForPauseOnTestFailure("RuntimeError", "Uh-oh...");
      }
    });
  }

  @Test
  public void testPyTestDontStopOnIgnoredException() {
    runPythonTest(new PyTestDebuggingTask("test_case_8.py") {
      @Override
      public void testing() throws Exception {
        waitForTerminate();
      }
    });
  }

  @Test
  public void testPyTestDontStopOnFailuresMarkedWithSkipAndXfail() {
    runPythonTest(new PyTestDebuggingTask("test_case_9.py") {
      @Override
      public void testing() throws Exception {
        waitForTerminate();
      }
    });
  }

  @Test
  public void testPyTestXdist() {
    runPythonTest(new PyTestDebuggingTask("test_case_10.py") {
      @Override
      public void testing() throws Exception {
        waitForPauseOnTestFailure("AssertionError", "assert False");
      }

      @Override
      public @NotNull Set<String> getTags() {
        return Collections.singleton("xdist");
      }
    });
  }

  @Test
  public void testErrorInUserCodeCalledFromFixture() {
    runPythonTest(new PyTestDebuggingTask("test_case_11.py") {
      @Override
      public void testing() throws Exception {
        waitForPauseOnTestFailure("IndexError", "list index out of range");
      }
    });
  }

  @Test
  public void testErrorInUserCodeCalledFromSetUp() {
    runPythonTest(new PyUnitTestDebuggingTask("test_case_12.py", "MyTestCase") {
      @Override
      public void testing() throws Exception {
        waitForPauseOnTestFailure("RuntimeError", "Boom!");
      }
    });
  }

  @Test
  public void testUnitTestDontStopOnExpectedError() {
    runPythonTest(new PyUnitTestDebuggingTask("test_case_13.py", "ExpectedFailureTestCase") {
      @Override
      public void testing() throws Exception {
        waitForTerminate();
      }

      @Override
      public @NotNull Set<String> getTags() {
        //noinspection SpellCheckingInspection
        return ImmutableSet.of("pytest", "python3");
      }
    });
  }

  @Test
  public void testTestWithImportError() {
    runPythonTest(new PyUnitTestDebuggingTask("test_case_14.py", "MyTestCase") {
      @Override
      public void testing() throws Exception {
        waitForPauseOnTestFailure("ModuleNotFoundError", "No module named 'foo'");
      }
    });
  }

  @Test
  public void testDidntRaiseExpectedExceptionPython3() {
    runPythonTest(new PyTestDebuggingTask("test_case_15.py") {
      @Override
      public void testing() throws Exception {
        waitForPause();
        Assert.assertTrue(eval("__exception__").getValue().startsWith(
          "(<class 'Failed'>, DID NOT RAISE <class 'Exception'>"));
      }

      @Override
      public @NotNull Set<String> getTags() {
        //noinspection SpellCheckingInspection
        return ImmutableSet.of("pytest", "python3");
      }
    });
  }

  @Test
  public void testDidntRaiseExpectedExceptionPython2() {
    runPythonTest(new PyTestDebuggingTask("test_case_15.py") {
      @Override
      public void testing() throws Exception {
        waitForPause();
        Assert.assertTrue(eval("__exception__").getValue().startsWith(
          "(<class 'builtins.Failed'>, DID NOT RAISE <type 'exceptions.Exception'>"));
      }

      @Override
      public @NotNull Set<String> getTags() {
        //noinspection SpellCheckingInspection
        return ImmutableSet.of("pytest", "python2.7");
      }
    });
  }

  @Test
  public void testUnitTestUnexpectedExceptionInTestPython3() {
    runPythonTest(new PyUnitTestDebuggingTask("test_case_16.py", "TestCase") {
      @Override
      public void testing() throws Exception {
        waitForPauseOnTestFailure("ZeroDivisionError", "division by zero");
      }

      @Override
      public @NotNull Set<String> getTags() {
        return Collections.singleton("python3");
      }
    });
  }

  @Test
  public void testUnitTestUnexpectedExceptionInTestPython2() {
    runPythonTest(new PyUnitTestDebuggingTask("test_case_16.py", "TestCase") {
      @Override
      public void testing() throws Exception {
        waitForPauseOnTestFailure("ZeroDivisionError", "integer division or modulo by zero");
      }

      @Override
      public @NotNull Set<String> getTags() {
        return Collections.singleton("python2.7");
      }
    });
  }

  @Test
  public void testStopOnExceptionNotListedInXfailDecorator() {
    runPythonTest(new PyTestDebuggingTask("test_case_17.py") {
      @Override
      public void testing() throws Exception {
        waitForPauseOnTestFailure("ValueError", "invalid literal for int() with base 10: 'Hello, World'");
      }
    });
  }
}
