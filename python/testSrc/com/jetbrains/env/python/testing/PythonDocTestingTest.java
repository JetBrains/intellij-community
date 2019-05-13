package com.jetbrains.env.python.testing;

import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.PyProcessWithConsoleTestTask;
import com.jetbrains.env.ut.PyDocTestProcessRunner;
import com.jetbrains.python.testing.AbstractPythonLegacyTestRunConfiguration;
import com.jetbrains.python.testing.doctest.PythonDocTestRunConfiguration;
import com.jetbrains.python.tools.sdkTools.SdkCreationType;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * User : catherine
 */
public final class PythonDocTestingTest extends PyEnvTestCase {


  @Test
  public void testConfigurationProducer() {
    runPythonTest(
      new CreateConfigurationByFileTask<>(null, PythonDocTestRunConfiguration.class, "doctest_test.py"));
  }

  // ensure no pattern provided if checkbox disabled
  @Test
  public void testNoPatternIfDisabled() {
    runPythonTest(new PyProcessWithConsoleTestTask<PyDocTestProcessRunner>("/testRunner/env/doc", SdkCreationType.EMPTY_SDK) {
      @NotNull
      @Override
      protected PyDocTestProcessRunner createProcessRunner() {
        return new PyDocTestProcessRunner("subfolder", 0) {
          @Override
          protected void configurationCreatedAndWillLaunch(@NotNull PythonDocTestRunConfiguration configuration) throws IOException {
            super.configurationCreatedAndWillLaunch(configuration);
            configuration.setTestType(AbstractPythonLegacyTestRunConfiguration.TestType.TEST_FOLDER);
            configuration.setPattern("ABC123");
            configuration.usePattern(false);
          }
        };
      }

      @Override
      protected void checkTestResults(@NotNull final PyDocTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all, int exitCode) {
        Assert.assertThat("Pattern used while it should not", all, Matchers.not(Matchers.containsString("ABC123")));
      }
    });
  }

  @Test
  public void testUTRunner() {
    runPythonTest(new PyProcessWithConsoleTestTask<PyDocTestProcessRunner>("/testRunner/env/doc", SdkCreationType.EMPTY_SDK) {
      @NotNull
      @Override
      protected PyDocTestProcessRunner createProcessRunner() {
        return new PyDocTestProcessRunner("test1.py", 0);
      }

      @Override
      protected void checkTestResults(@NotNull final PyDocTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all, int exitCode) {
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
      protected PyDocTestProcessRunner createProcessRunner() {
        return new PyDocTestProcessRunner("test1.py::FirstGoodTest", 0);
      }

      @Override
      protected void checkTestResults(@NotNull final PyDocTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all, int exitCode) {
        assertEquals(1, runner.getAllTestsCount());
        assertEquals(1, runner.getPassedTestsCount());
      }
    });
  }

  @Test
  public void testDiff() {
    runPythonTest(new PyProcessWithConsoleTestTask<PyDocTestProcessRunner>("/testRunner/env/doc", SdkCreationType.EMPTY_SDK) {
      @NotNull
      @Override
      protected PyDocTestProcessRunner createProcessRunner() {
        return new PyDocTestProcessRunner("test_Diff.py::test_dff", 0);
      }

      @Override
      protected void checkTestResults(@NotNull final PyDocTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all, int exitCode) {
        final String text = runner.getConsole().getText();
        Assert.assertThat("No diff link", text, Matchers.containsString("<Click to see difference>"));
        Assert.assertThat("Wrong actual", text, Matchers.containsString("Got"));
        Assert.assertThat("Wrong actual", text, Matchers.containsString("2"));
        Assert.assertThat("Wrong expected", text, Matchers.containsString("Expected"));
        Assert.assertThat("Wrong expected", text, Matchers.containsString("3"));
      }
    });
  }

  @Test
  public void testMethod() {
    runPythonTest(new PyProcessWithConsoleTestTask<PyDocTestProcessRunner>("/testRunner/env/doc", SdkCreationType.EMPTY_SDK) {
      @NotNull
      @Override
      protected PyDocTestProcessRunner createProcessRunner() {
        return new PyDocTestProcessRunner("test1.py::SecondGoodTest::test_passes", 0);
      }

      @Override
      protected void checkTestResults(@NotNull final PyDocTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all, int exitCode) {
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
      protected PyDocTestProcessRunner createProcessRunner() {
        return new PyDocTestProcessRunner("test1.py::factorial", 0);
      }

      @Override
      protected void checkTestResults(@NotNull final PyDocTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all, int exitCode) {
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
      protected PyDocTestProcessRunner createProcessRunner() {
        return new PyDocTestProcessRunner("test2.py", 0);
      }

      @Override
      protected void checkTestResults(@NotNull final PyDocTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all, int exitCode) {
        assertEquals(3, runner.getAllTestsCount());
        assertEquals(1, runner.getPassedTestsCount());
        assertEquals(2, runner.getFailedTestsCount());
      }
    });
  }
}
