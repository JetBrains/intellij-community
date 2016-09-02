package com.jetbrains.env.python.testing;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.sm.runner.ui.MockPrinter;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.env.EnvTestTagsRequired;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.PyProcessWithConsoleTestTask;
import com.jetbrains.env.ut.PyTestTestProcessRunner;
import com.jetbrains.python.sdkTools.SdkCreationType;
import com.jetbrains.python.testing.PythonTestConfigurationsModel;
import com.jetbrains.python.testing.pytest.PyTestConfigurationProducer;
import com.jetbrains.python.testing.pytest.PyTestRunConfiguration;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * User : catherine
 */
@EnvTestTagsRequired(tags = "pytest")
public class PythonPyTestingTest extends PyEnvTestCase {

  @Test
  public void testConfigurationProducer() throws Exception {
    runPythonTest(
      new CreateConfigurationTestTask(PyTestConfigurationProducer.class, PythonTestConfigurationsModel.PY_TEST_NAME));
  }

  // Import error should lead to test failure
  @Test
  public void testFailInCaseOfError() {
    runPythonTest(new PyProcessWithConsoleTestTask<PyTestTestProcessRunner>("/testRunner/env/pytest/failTest", SdkCreationType.EMPTY_SDK) {

      @NotNull
      @Override
      protected PyTestTestProcessRunner createProcessRunner() throws Exception {
        return new PyTestTestProcessRunner(".", 0);
      }


      @Override
      protected void checkTestResults(@NotNull PyTestTestProcessRunner runner,
                                      @NotNull String stdout,
                                      @NotNull String stderr,
                                      @NotNull String all) {
        Assert.assertThat("Import error is not marked as error", runner.getFailedTestsCount(), Matchers.greaterThanOrEqualTo(1));
      }
    });
  }

  /**
   * Ensure project dir is used as curdir even if not set explicitly
   */
  @Test
  public void testCurrentDir() throws Exception {
    runPythonTest(new PyProcessWithConsoleTestTask<PyTestTestProcessRunner>("/testRunner/env/pytest/", SdkCreationType.EMPTY_SDK) {
      @NotNull
      @Override
      protected PyTestTestProcessRunner createProcessRunner() throws Exception {
        return new PyTestTestProcessRunner("", 0) {
          @Override
          protected void configurationCreatedAndWillLaunch(@NotNull final PyTestRunConfiguration configuration) throws IOException {
            super.configurationCreatedAndWillLaunch(configuration);
            configuration.setWorkingDirectory(null);
            final VirtualFile fullFilePath = myFixture.getTempDirFixture().getFile("dir_test.py");
            assert fullFilePath != null : String.format("No dir_test.py in %s", myFixture.getTempDirFixture().getTempDirPath());
            configuration.setTestToRun(fullFilePath.getPath());
          }
        };
      }

      @Override
      protected void checkTestResults(@NotNull final PyTestTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        Assert.assertThat("No directory found in output", stdout,
                          Matchers.containsString(String.format("Directory %s", myFixture.getTempDirPath())));
      }
    });
  }

  @Test
  public void testPytestRunner() {

    runPythonTest(new PyProcessWithConsoleTestTask<PyTestTestProcessRunner>("/testRunner/env/pytest", SdkCreationType.EMPTY_SDK) {
      @NotNull
      @Override
      protected PyTestTestProcessRunner createProcessRunner() throws Exception {
        return new PyTestTestProcessRunner("test1.py", 0);
      }

      @Override
      protected void checkTestResults(@NotNull final PyTestTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        assertEquals(3, runner.getAllTestsCount());
        assertEquals(3, runner.getPassedTestsCount());
        runner.assertAllTestsPassed();


        // This test has "sleep(1)", so duration should be >=1000
        final AbstractTestProxy testForOneSecond = runner.findTestByName("testOne");
        Assert.assertThat("Wrong duration", testForOneSecond.getDuration(), Matchers.greaterThanOrEqualTo(1000L));
      }
    });
  }

  @Test
  public void testPytestRunner2() {
    runPythonTest(new PyProcessWithConsoleTestTask<PyTestTestProcessRunner>("/testRunner/env/pytest", SdkCreationType.EMPTY_SDK) {
      @NotNull
      @Override
      protected PyTestTestProcessRunner createProcessRunner() throws Exception {
        return new PyTestTestProcessRunner("test2.py", 1);
      }

      @Override
      protected void checkTestResults(@NotNull final PyTestTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        if (runner.getCurrentRerunStep() > 0) {
          /**
           * We can't rerun one subtest (yield), so we rerun whole "test_even"
           */
          assertEquals(stderr, 7, runner.getAllTestsCount());
          assertEquals(stderr, 3, runner.getPassedTestsCount());
          assertEquals(stderr, 4, runner.getFailedTestsCount());
          return;
        }
        assertEquals(stderr, 9, runner.getAllTestsCount());
        assertEquals(stderr, 5, runner.getPassedTestsCount());
        assertEquals(stderr, 4, runner.getFailedTestsCount());
        Assert
          .assertThat("No test stdout", MockPrinter.fillPrinter(runner.findTestByName("testOne")).getStdOut(),
                      Matchers.startsWith("I am test1"));

        // Ensure test has stdout even it fails
        final AbstractTestProxy testFail = runner.findTestByName("testFail");
        Assert.assertThat("No stdout for fail", MockPrinter.fillPrinter(testFail).getStdOut(),
                          Matchers.startsWith("I will fail"));

        // This test has "sleep(1)", so duration should be >=1000
        Assert.assertThat("Wrong duration", testFail.getDuration(), Matchers.greaterThanOrEqualTo(1000L));
      }
    });
  }


  /**
   * Ensures file references are highlighted for pytest traceback
   */
  @Test
  public void testPyTestFileReferences() {
    final String fileName = "reference_tests.py";

    runPythonTest(new PyProcessWithConsoleTestTask<PyTestTestProcessRunner>("/testRunner/env/unit", SdkCreationType.EMPTY_SDK) {
      @NotNull
      @Override
      protected PyTestTestProcessRunner createProcessRunner() throws Exception {
        return new PyTestTestProcessRunner(fileName, 0);
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
