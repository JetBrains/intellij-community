package com.jetbrains.env.python.testing;

import com.intellij.execution.testframework.sm.runner.ui.MockPrinter;
import com.jetbrains.env.EnvTestTagsRequired;
import com.jetbrains.env.PyProcessWithConsoleTestTask;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.ut.PyTestTestProcessRunner;
import com.jetbrains.python.sdkTools.SdkCreationType;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.List;

/**
 * User : catherine
 */
@EnvTestTagsRequired(tags = "pytest")
public class PythonPyTestingTest extends PyEnvTestCase {
  public void testPytestRunner() {

    runPythonTest(new PyProcessWithConsoleTestTask<PyTestTestProcessRunner>(SdkCreationType.EMPTY_SDK) {
      @NotNull
      @Override
      protected PyTestTestProcessRunner createProcessRunner() throws Exception {
        return new PyTestTestProcessRunner(getTestDataPath() + "/testRunner/env/pytest", "test1.py", 0);
      }

      @Override
      protected void checkTestResults(@NotNull final PyTestTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        assertEquals(3, runner.getAllTestsCount());
        assertEquals(3, runner.getPassedTestsCount());
        runner.assertAllTestsPassed();
      }
    });
  }

  public void testPytestRunner2() {
    runPythonTest(new PyProcessWithConsoleTestTask<PyTestTestProcessRunner>(SdkCreationType.EMPTY_SDK) {
      @NotNull
      @Override
      protected PyTestTestProcessRunner createProcessRunner() throws Exception {
        return new PyTestTestProcessRunner(getTestDataPath() + "/testRunner/env/pytest", "test2.py", 0);
      }

      @Override
      protected void checkTestResults(@NotNull final PyTestTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        assertEquals(9, runner.getAllTestsCount());
        assertEquals(5, runner.getPassedTestsCount());
        assertEquals(4, runner.getFailedTestsCount());
        Assert
          .assertThat("No test stdout", MockPrinter.fillPrinter(runner.findTestByName("testOne")).getStdOut(),
                      Matchers.startsWith("I am test1"));

        // Ensure test has stdout even it fails
        Assert.assertThat("No stdout for fail", MockPrinter.fillPrinter(runner.findTestByName("testFail")).getStdOut(),
                          Matchers.startsWith("I will fail"));
      }
    });
  }


  /**
   * Ensures file references are highlighted for pytest traceback
   */
  public void testPyTestFileReferences() {
    final String fileName = "reference_tests.py";

    runPythonTest(new PyProcessWithConsoleTestTask<PyTestTestProcessRunner>(SdkCreationType.EMPTY_SDK) {
      @NotNull
      @Override
      protected PyTestTestProcessRunner createProcessRunner() throws Exception {
        return new PyTestTestProcessRunner(getTestDataPath() + "/testRunner/env/unit", fileName, 0);
      }

      @Override
      protected void checkTestResults(@NotNull final PyTestTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        final List<String> fileNames = runner.getHighlightedStringsInConsole().second;
        Assert.assertThat("No lines highlighted", fileNames, Matchers.not(Matchers.empty()));
        // PyTest highlights file:line_number
        Assert.assertTrue("Assert fail not marked", fileNames.contains("reference_tests.py:7"));
        Assert.assertTrue("Failed test not marked", fileNames.contains("reference_tests.py:12"));
        Assert.assertTrue("Failed test not marked", fileNames.contains("reference_tests.py"));
      }
    });
  }
}
