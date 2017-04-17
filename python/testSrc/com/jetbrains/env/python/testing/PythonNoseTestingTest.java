package com.jetbrains.env.python.testing;

import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.jetbrains.env.EnvTestTagsRequired;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.PyProcessWithConsoleTestTask;
import com.jetbrains.env.python.testing.CreateConfigurationTestTask.PyConfigurationCreationTask;
import com.jetbrains.env.ut.PyNoseTestProcessRunner;
import com.jetbrains.env.ut.PyUnitTestProcessRunner;
import com.jetbrains.python.sdkTools.SdkCreationType;
import com.jetbrains.python.testing.PythonTestConfigurationsModel;
import com.jetbrains.python.testing.universalTests.PyUniversalNoseTestConfiguration;
import com.jetbrains.python.testing.universalTests.PyUniversalNoseTestFactory;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * User : catherine
 */
@EnvTestTagsRequired(tags = "nose")
public final class PythonNoseTestingTest extends PyEnvTestCase {

  // Ensures setup/teardown does not break anything
  @Test
  public void testSetupTearDown() throws Exception {
    runPythonTest(new SetupTearDownTestTask<PyNoseTestProcessRunner>() {
      @NotNull
      @Override
      protected PyNoseTestProcessRunner createProcessRunner() throws Exception {
        return new PyNoseTestProcessRunner("test_test.py", 1);
      }
    });
  }

  /**
   * Ensures that python target pointing to module works correctly
   */
  @Test
  public void testRunModuleAsFile() throws Exception {
    runPythonTest(new RunModuleAsFileTask<PyNoseTestProcessRunner>(){
      @NotNull
      @Override
      protected PyNoseTestProcessRunner createProcessRunner() throws Exception {
        return new PyNoseTestProcessRunner(TARGET, 0);
      }
    });
  }

  @Test
  public void testRerunSubfolder() throws Exception {
    runPythonTest(new RerunSubfolderTask<PyNoseTestProcessRunner>(2) {
      @NotNull
      @Override
      protected PyNoseTestProcessRunner createProcessRunner() throws Exception {
        return new PyNoseTestProcessRunner(".", 1);
      }
    });
  }

  // Ensure slow test is not run when --attr="!slow"  is provided
  @Test
  public void testMarkerWithSlow() throws Exception {
    runPythonTest(
      new PyProcessWithConsoleTestTask<PyNoseTestProcessRunner>("/testRunner/env/nose/test_with_slow", SdkCreationType.EMPTY_SDK) {

        @NotNull
        @Override
        protected PyNoseTestProcessRunner createProcessRunner() throws Exception {
          return new PyNoseTestProcessRunner("test_with_slow.py", 0) {
            @Override
            protected void configurationCreatedAndWillLaunch(@NotNull PyUniversalNoseTestConfiguration configuration) throws IOException {
              super.configurationCreatedAndWillLaunch(configuration);
              configuration.setAdditionalArguments("--attr=\"!slow\"");
            }
          };
        }


        @Override
        protected void checkTestResults(@NotNull PyNoseTestProcessRunner runner,
                                        @NotNull String stdout,
                                        @NotNull String stderr,
                                        @NotNull String all) {
          Assert.assertEquals("--slow runner borken", "Test tree:\n" +
                                                      "[root]\n" +
                                                      ".test_with_slow\n" +
                                                      "..test_fast(+)\n",
                              runner.getFormattedTestTree());
        }
      });
  }


  @Test
  public void testMultipleCases() throws Exception {
    runPythonTest(
      new CreateConfigurationMultipleCasesTask<>(PythonTestConfigurationsModel.PYTHONS_NOSETEST_NAME,
                                                 PyUniversalNoseTestConfiguration.class));
  }

  /**
   * Checks tests are resolved when launched from subfolder
   */
  @Test
  public void testTestsInSubFolderResolvable() throws Exception {
    runPythonTest(
      new PyUnitTestProcessWithConsoleTestTask.PyTestsInSubFolderRunner<PyNoseTestProcessRunner>("test_metheggs", "test_funeggs",
                                                                                                 "test_first") {
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
      new CreateConfigurationByFileTask<>(PythonTestConfigurationsModel.PYTHONS_NOSETEST_NAME, PyUniversalNoseTestConfiguration.class));
  }

  @Test
  public void testConfigurationProducerOnDirectory() throws Exception {
    runPythonTest(
      new CreateConfigurationByFileTask.CreateConfigurationTestAndRenameFolderTask<>(PythonTestConfigurationsModel.PYTHONS_NOSETEST_NAME,
                                                                                     PyUniversalNoseTestConfiguration.class));
  }

  @Test
  public void testRenameClass() throws Exception {
    runPythonTest(
      new CreateConfigurationByFileTask.CreateConfigurationTestAndRenameClassTask<>(
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
