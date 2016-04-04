package com.jetbrains.env.python.testing;

import com.jetbrains.env.EnvTestTagsRequired;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.PyProcessWithConsoleTestTask;
import com.jetbrains.env.ut.PyNoseTestProcessRunner;
import com.jetbrains.python.sdkTools.SdkCreationType;
import com.jetbrains.python.testing.PythonTestConfigurationsModel;
import com.jetbrains.python.testing.nosetest.PythonNoseTestConfigurationProducer;
import org.jetbrains.annotations.NotNull;

/**
 * User : catherine
 */
@EnvTestTagsRequired(tags = "nose")
public final class PythonNoseTestingTest extends PyEnvTestCase {


  public void testConfigurationProducer() throws Exception {
    runPythonTest(
      new CreateConfigurationTestTask(PythonNoseTestConfigurationProducer.class, PythonTestConfigurationsModel.PYTHONS_NOSETEST_NAME));
  }

  public void testNoseRunner() {

    runPythonTest(new PyProcessWithConsoleTestTask<PyNoseTestProcessRunner>(SdkCreationType.EMPTY_SDK) {

      @NotNull
      @Override
      protected PyNoseTestProcessRunner createProcessRunner() throws Exception {
        return new PyNoseTestProcessRunner(getTestDataPath() + "/testRunner/env/nose", "test1.py", 0);
      }

      @Override
      protected void checkTestResults(@NotNull final PyNoseTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        assertEquals(3, runner.getAllTestsCount());
        assertEquals(3, runner.getPassedTestsCount());
        runner.assertAllTestsPassed();
      }
    });
  }

  public void testNoseRunner2() {
    runPythonTest(new PyProcessWithConsoleTestTask<PyNoseTestProcessRunner>(SdkCreationType.EMPTY_SDK) {
      @NotNull
      @Override
      protected PyNoseTestProcessRunner createProcessRunner() throws Exception {
        return new PyNoseTestProcessRunner(getTestDataPath() + "/testRunner/env/nose", "test2.py", 0);
      }

      @Override
      protected void checkTestResults(@NotNull final PyNoseTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        assertEquals(8, runner.getAllTestsCount());
        assertEquals(5, runner.getPassedTestsCount());
        assertEquals(3, runner.getFailedTestsCount());
      }
    });
  }
}
