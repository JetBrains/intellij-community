package com.jetbrains.env.python.testing;

import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.jetbrains.env.EnvTestTagsRequired;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.PyProcessWithConsoleTestTask;
import com.jetbrains.env.python.testing.CreateConfigurationTestTask.PyConfigurationCreationTask;
import com.jetbrains.env.ut.PyNoseTestProcessRunner;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.sdkTools.SdkCreationType;
import com.jetbrains.python.testing.PyNoseTestConfiguration;
import com.jetbrains.python.testing.PyNoseTestFactory;
import com.jetbrains.python.testing.PyTestFrameworkService;
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


  private final String myFrameworkName = PyTestFrameworkService.getSdkReadableNameByFramework(PyNames.NOSE_TEST);;

  @Test
  public void testNoseGenerators() {
    runPythonTest(new PyProcessWithConsoleTestTask<PyNoseTestProcessRunner>("/testRunner/env/nose/generators", SdkCreationType.EMPTY_SDK) {
      @NotNull
      @Override
      protected PyNoseTestProcessRunner createProcessRunner() {
        return new PyNoseTestProcessRunner(toFullPath("test_nose_generator.py"), 1);
      }

      @Override
      protected void checkTestResults(@NotNull final PyNoseTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        Assert.assertEquals("Nose genenerator produced bad tree", "Test tree:\n" +
                                "[root]\n" +
                                ".test_nose_generator\n" +
                                "..test_evens\n" +
                                "...(0, 0)(+)\n" +
                                "...(1, 3)(-)\n" +
                                "...(2, 6)(+)\n" +
                                "...(3, 9)(-)\n" +
                                "...(4, 12)(+)\n", runner.getFormattedTestTree());
      }
    });
  }

  // Ensures setup/teardown does not break anything
  @Test
  public void testSetupTearDown() {
    runPythonTest(new SetupTearDownTestTask<PyNoseTestProcessRunner>() {
      @NotNull
      @Override
      protected PyNoseTestProcessRunner createProcessRunner() {
        return new PyNoseTestProcessRunner("test_test.py", 1);
      }
    });
  }

  /**
   * Ensures that python target pointing to module works correctly
   */
  @Test
  public void testRunModuleAsFile() {
    runPythonTest(new RunModuleAsFileTask<PyNoseTestProcessRunner>(){
      @NotNull
      @Override
      protected PyNoseTestProcessRunner createProcessRunner() {
        return new PyNoseTestProcessRunner(TARGET, 0);
      }
    });
  }

  @Test
  public void testRerunSubfolder() {
    runPythonTest(new RerunSubfolderTask<PyNoseTestProcessRunner>(2) {
      @NotNull
      @Override
      protected PyNoseTestProcessRunner createProcessRunner() {
        return new PyNoseTestProcessRunner(".", 1);
      }
    });
  }

  // Ensure slow test is not run when --attr="!slow"  is provided
  @Test
  public void testMarkerWithSlow() {
    runPythonTest(new SlowRunnerTask("--attr=\"!slow\" -vvv"));
  }
  @Test
  public void testMarkerWithSlowSingleQuotes() {
    runPythonTest(new SlowRunnerTask("--attr='!slow' -vvv"));
  }
  @Test
  public void testMarkerWithSlowRegexp() {
    runPythonTest(new SlowRunnerTask("--attr='!slow' -vvv -m \"(?:^|[\\b_\\./-])[Tt]est\""));
  }

  @Test
  public void testMultipleCases() {
    runPythonTest(
      new CreateConfigurationMultipleCasesTask<>(PyTestFrameworkService.getSdkReadableNameByFramework(PyNames.NOSE_TEST),
                                                 PyNoseTestConfiguration.class));
  }

  /**
   * Checks tests are resolved when launched from subfolder
   */
  @Test
  public void testTestsInSubFolderResolvable() {
    runPythonTest(
      new PyTestsInSubFolderRunner<PyNoseTestProcessRunner>("test_metheggs", "test_funeggs",
                                                            "test_first") {
        @NotNull
        @Override
        protected PyNoseTestProcessRunner createProcessRunner() {
          return new PyNoseTestProcessRunner(toFullPath("tests"), 0) {
            @Override
            protected void configurationCreatedAndWillLaunch(@NotNull PyNoseTestConfiguration configuration) throws IOException {
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
  public void testOutput() {
    runPythonTest(
      new PyTestsOutputRunner<PyNoseTestProcessRunner>("test_metheggs", "test_funeggs", "test_first") {
        @NotNull
        @Override
        protected PyNoseTestProcessRunner createProcessRunner() {
          return new PyNoseTestProcessRunner(toFullPath("tests"), 0) {
            @Override
            protected void configurationCreatedAndWillLaunch(@NotNull PyNoseTestConfiguration configuration) throws IOException {
              super.configurationCreatedAndWillLaunch(configuration);
              configuration.setWorkingDirectory(getWorkingFolderForScript());
            }
          };
        }
      });
  }


  @Test(expected = RuntimeConfigurationWarning.class)
  public void testValidation() {

    final PyConfigurationCreationTask<PyNoseTestConfiguration> task =
      new PyConfigurationCreationTask<PyNoseTestConfiguration>() {
        @NotNull
        @Override
        protected PyNoseTestFactory createFactory() {
          return PyNoseTestFactory.INSTANCE;
        }
      };
    runPythonTest(task);
    task.checkEmptyTarget();
  }

  @Test
  public void testConfigurationProducer() {
    runPythonTest(
      new CreateConfigurationByFileTask<>(myFrameworkName, PyNoseTestConfiguration.class));
  }

  @Test
  public void testConfigurationProducerOnDirectory() {
    runPythonTest(
      new CreateConfigurationByFileTask.CreateConfigurationTestAndRenameFolderTask<>(myFrameworkName,
                                                                                     PyNoseTestConfiguration.class));
  }

  @Test
  public void testRenameClass() {
    runPythonTest(
      new CreateConfigurationByFileTask.CreateConfigurationTestAndRenameClassTask<>(
        myFrameworkName,
        PyNoseTestConfiguration.class));
  }

  @Test
  public void testNoseRunner() {

    runPythonTest(new PyProcessWithConsoleTestTask<PyNoseTestProcessRunner>("/testRunner/env/nose", SdkCreationType.EMPTY_SDK) {

      @NotNull
      @Override
      protected PyNoseTestProcessRunner createProcessRunner() {
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
      protected PyNoseTestProcessRunner createProcessRunner() {
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

  private static class SlowRunnerTask extends PyProcessWithConsoleTestTask<PyNoseTestProcessRunner> {
    @NotNull
    private final String myArguments;

    SlowRunnerTask(@NotNull final String arguments) {
      super("/testRunner/env/nose/test_with_slow", SdkCreationType.EMPTY_SDK);
      myArguments = arguments;
    }

    @NotNull
    @Override
    protected PyNoseTestProcessRunner createProcessRunner() {
      return new PyNoseTestProcessRunner("test_with_slow.py", 0) {
        @Override
        protected void configurationCreatedAndWillLaunch(@NotNull PyNoseTestConfiguration configuration) throws IOException {
          super.configurationCreatedAndWillLaunch(configuration);
          configuration.setAdditionalArguments(myArguments);
        }
      };
    }


    @Override
    protected void checkTestResults(@NotNull PyNoseTestProcessRunner runner,
                                    @NotNull String stdout,
                                    @NotNull String stderr,
                                    @NotNull String all) {
      Assert.assertEquals("--slow runner broken on arguments" + myArguments, "Test tree:\n" +
                                                  "[root]\n" +
                                                  ".test_with_slow\n" +
                                                  "..test_fast(+)\n",
                          runner.getFormattedTestTree());
    }
  }
}
