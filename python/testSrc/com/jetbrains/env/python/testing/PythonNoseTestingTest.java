package com.jetbrains.env.python.testing;

import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.jetbrains.env.EnvTestTagsRequired;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.PyProcessWithConsoleTestTask;
import com.jetbrains.env.python.testing.CreateConfigurationTestTask.PyConfigurationCreationTask;
import com.jetbrains.env.ut.PyNoseTestProcessRunner;
import com.jetbrains.python.sdkTools.SdkCreationType;
import com.jetbrains.python.testing.PythonTestConfigurationsModel;
import com.jetbrains.python.testing.universalTests.PyUniversalNoseTestConfiguration;
import com.jetbrains.python.testing.universalTests.PyUniversalNoseTestFactory;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * User : catherine
 */
@EnvTestTagsRequired(tags = "nose")
public final class PythonNoseTestingTest extends PyEnvTestCase {


  /**
   * Checks tests are resolved when launched from subfolder
   */
  @Test
  public void testTestsInSubFolderResolvable() throws Exception {
    runPythonTest(
      new PyUnitTestProcessWithConsoleTestTask.PyTestsInSubFolderRunner<PyNoseTestProcessRunner>("test_metheggs", "test_funeggs", "test_first") {
        @NotNull
        @Override
        protected PyNoseTestProcessRunner createProcessRunner() throws Exception {
          return new PyNoseTestProcessRunner(toFullPath("tests"), 0) {
            @Override
            protected void configurationCreatedAndWillLaunch(@NotNull PyUniversalNoseTestConfiguration configuration) throws IOException {
              super.configurationCreatedAndWillLaunch(configuration);
              configuration.setWorkingDirectory(getWorkingFolderForScript());
            }
          };
        }
      });
  }

  /**
   * Ensures test output works
   */
  @Test
  public void testOutput() throws Exception {
    runPythonTest(
      new PyUnitTestProcessWithConsoleTestTask.PyTestsOutputRunner<PyNoseTestProcessRunner>("test_metheggs", "test_funeggs", "test_first") {
        @NotNull
        @Override
        protected PyNoseTestProcessRunner createProcessRunner() throws Exception {
          return new PyNoseTestProcessRunner(toFullPath("tests"), 0) {
            @Override
            protected void configurationCreatedAndWillLaunch(@NotNull PyUniversalNoseTestConfiguration configuration) throws IOException {
              super.configurationCreatedAndWillLaunch(configuration);
              configuration.setWorkingDirectory(getWorkingFolderForScript());
            }
          };
        }
      });
  }


  @Test(expected = RuntimeConfigurationWarning.class)
  public void testValidation() throws Exception {

    final PyConfigurationCreationTask<PyUniversalNoseTestConfiguration> task =
      new PyConfigurationCreationTask<PyUniversalNoseTestConfiguration>() {
        @NotNull
        @Override
        protected PyUniversalNoseTestFactory createFactory() {
          return PyUniversalNoseTestFactory.INSTANCE;
        }
      };
    runPythonTest(task);
    task.checkEmptyTarget();
  }

  @Test
  public void testConfigurationProducer() throws Exception {
    runPythonTest(
      new CreateConfigurationTestTask<>(PythonTestConfigurationsModel.PYTHONS_NOSETEST_NAME, PyUniversalNoseTestConfiguration.class));
  }

  @Test
  public void testConfigurationProducerOnDirectory() throws Exception {
    runPythonTest(
      new CreateConfigurationTestTask.CreateConfigurationTestAndRenameFolderTask(PythonTestConfigurationsModel.PYTHONS_NOSETEST_NAME,
                                                                                 PyUniversalNoseTestConfiguration.class));
  }

  @Test
  public void testRenameClass() throws Exception {
    runPythonTest(
      new CreateConfigurationTestTask.CreateConfigurationTestAndRenameClassTask(
        PythonTestConfigurationsModel.PYTHONS_NOSETEST_NAME,
        PyUniversalNoseTestConfiguration.class));
  }

  @Test
  public void testNoseRunner() {

    runPythonTest(new PyProcessWithConsoleTestTask<PyNoseTestProcessRunner>("/testRunner/env/nose", SdkCreationType.EMPTY_SDK) {

      @NotNull
      @Override
      protected PyNoseTestProcessRunner createProcessRunner() throws Exception {
        return new PyNoseTestProcessRunner("test1.py", 0);
      }

      @Override
      protected void checkTestResults(@NotNull final PyNoseTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        assertEquals(4, runner.getAllTestsCount());
        assertEquals(3, runner.getPassedTestsCount());
      }
    });
  }

  @Test
  public void testNoseRunner2() {
    runPythonTest(new PyProcessWithConsoleTestTask<PyNoseTestProcessRunner>("/testRunner/env/nose", SdkCreationType.EMPTY_SDK) {
      @NotNull
      @Override
      protected PyNoseTestProcessRunner createProcessRunner() throws Exception {
        return new PyNoseTestProcessRunner("test2.py", 0);
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
