package com.jetbrains.env.python.testing;

import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.PyProcessWithConsoleTestTask;
import com.jetbrains.env.ut.PyDocTestProcessRunner;
import com.jetbrains.python.sdkTools.SdkCreationType;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * User : catherine
 */
public class PythonDocTestingTest extends PyEnvTestCase {
  @Test
  public void testUTRunner() {
    runPythonTest(new PyProcessWithConsoleTestTask<PyDocTestProcessRunner>("/testRunner/env/doc", SdkCreationType.EMPTY_SDK) {
      @NotNull
      @Override
      protected PyDocTestProcessRunner createProcessRunner() throws Exception {
        return new PyDocTestProcessRunner("test1.py", 0);
      }

      @Override
      protected void checkTestResults(@NotNull final PyDocTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        assertEquals(3, runner.getAllTestsCount());
        assertEquals(3, runner.getPassedTestsCount());
        runner.assertAllTestsPassed();
      }
    });
  }

  @Test
  public void testClass() {
    runPythonTest(new PyProcessWithConsoleTestTask<PyDocTestProcessRunner>("/testRunner/env/doc", SdkCreationType.EMPTY_SDK) {
      @NotNull
      @Override
      protected PyDocTestProcessRunner createProcessRunner() throws Exception {
        return new PyDocTestProcessRunner("test1.py::FirstGoodTest", 0);
      }

      @Override
      protected void checkTestResults(@NotNull final PyDocTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        assertEquals(1, runner.getAllTestsCount());
        assertEquals(1, runner.getPassedTestsCount());
      }
    });
  }

  @Test
  public void testMethod() {
    runPythonTest(new PyProcessWithConsoleTestTask<PyDocTestProcessRunner>("/testRunner/env/doc", SdkCreationType.EMPTY_SDK) {
      @NotNull
      @Override
      protected PyDocTestProcessRunner createProcessRunner() throws Exception {
        return new PyDocTestProcessRunner("test1.py::SecondGoodTest::test_passes", 0);
      }

      @Override
      protected void checkTestResults(@NotNull final PyDocTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        assertEquals(1, runner.getAllTestsCount());
        assertEquals(1, runner.getPassedTestsCount());
      }
    });
  }

  @Test
  public void testFunction() {
    runPythonTest(new PyProcessWithConsoleTestTask<PyDocTestProcessRunner>("/testRunner/env/doc", SdkCreationType.EMPTY_SDK) {
      @NotNull
      @Override
      protected PyDocTestProcessRunner createProcessRunner() throws Exception {
        return new PyDocTestProcessRunner("test1.py::factorial", 0);
      }

      @Override
      protected void checkTestResults(@NotNull final PyDocTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        assertEquals(1, runner.getAllTestsCount());
        assertEquals(1, runner.getPassedTestsCount());
      }
    });
  }

  @Test
  public void testUTRunner2() {
    runPythonTest(new PyProcessWithConsoleTestTask<PyDocTestProcessRunner>("/testRunner/env/doc", SdkCreationType.EMPTY_SDK) {
      @NotNull
      @Override
      protected PyDocTestProcessRunner createProcessRunner() throws Exception {
        return new PyDocTestProcessRunner("test2.py", 0);
      }

      @Override
      protected void checkTestResults(@NotNull final PyDocTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        assertEquals(3, runner.getAllTestsCount());
        assertEquals(1, runner.getPassedTestsCount());
        assertEquals(2, runner.getFailedTestsCount());
      }
    });
  }
}
