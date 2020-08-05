// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.env.python.debug;

import com.google.common.collect.ImmutableSet;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.Staging;
import com.jetbrains.python.testing.PyAbstractTestFactory;
import com.jetbrains.python.testing.PyTestFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;

import java.util.Set;

import static com.jetbrains.env.python.debug.PyUnitTestDebuggingTask.PythonExceptionData;

public class PythonUnitTestsDebuggingTest extends PyEnvTestCase {
  @Test
  public void testPythonExceptionDataClass() {
    PythonExceptionData pythonExceptionModel = PythonExceptionData.fromString(
      "(<class 'foo'>, AssertionError('bar is not baz'), <traceback object at 0x123456789>)");
    Assert.assertEquals("foo", pythonExceptionModel.getExceptionClass());
    Assert.assertEquals("bar is not baz", pythonExceptionModel.getErrorMessage());
    Assert.assertNull(PythonExceptionData.fromString("foobar"));
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

  @Staging
  @Test
  public void testUnitTestFalseIsNotTrue() {
    runPythonTest(new PyUnitTestDebuggingTask("test_case_2.py", "MyTestCase") {
      @Override
      public void testing() throws Exception {
        waitForPauseOnTestFailure("AssertionError", "False is not true");
      }
    });
  }

  @Staging
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

  @Staging
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

  @Staging
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
        return ImmutableSet.of("xdist");
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

  @Staging
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

  @Staging
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
        return ImmutableSet.of("python3");
      }
    });
  }

  @Staging
  @Test
  public void testUnitTestUnexpectedExceptionInTestPython2() {
    runPythonTest(new PyUnitTestDebuggingTask("test_case_16.py", "TestCase") {
      @Override
      public void testing() throws Exception {
        waitForPauseOnTestFailure("ZeroDivisionError", "integer division or modulo by zero");
      }

      @Override
      public @NotNull Set<String> getTags() {
        return ImmutableSet.of("python2.7");
      }
    });
  }

  @Staging
  @Test
  public void testStopOnExceptionNotListedInXfailDecorator() {
    runPythonTest(new PyTestDebuggingTask("test_case_17.py") {
      @Override
      public void testing() throws Exception {
        waitForPauseOnTestFailure("ValueError", "invalid literal for int() with base 10: 'Hello, World'");
      }
    });
  }

  private static class PyTestDebuggingTask extends PyUnitTestDebuggingTask {
    PyTestDebuggingTask(@NotNull String scriptName) {
      this(scriptName, null);
    }

    PyTestDebuggingTask(@NotNull String scriptName, @Nullable String targetName) {
      super(scriptName, targetName);
    }

    @Override
    protected Class<? extends PyAbstractTestFactory<?>> getRunConfigurationFactoryClass() {
      return PyTestFactory.class;
    }

    @Override
    public @NotNull Set<String> getTags() {
      //noinspection SpellCheckingInspection
      return ImmutableSet.of("pytest");
    }
  }
}
