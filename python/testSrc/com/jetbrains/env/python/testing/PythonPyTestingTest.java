package com.jetbrains.env.python.testing;

import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.sm.runner.ui.MockPrinter;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.util.PathUtil;
import com.jetbrains.env.EnvTestTagsRequired;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.PyProcessWithConsoleTestTask;
import com.jetbrains.env.ut.PyTestTestProcessRunner;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.testing.PyTestConfiguration;
import com.jetbrains.python.testing.PyTestFactory;
import com.jetbrains.python.testing.PyTestFrameworkService;
import com.jetbrains.python.testing.TestTargetType;
import com.jetbrains.python.tools.sdkTools.SdkCreationType;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static com.jetbrains.env.ut.PyScriptTestProcessRunner.TEST_TARGET_PREFIX;
import static org.junit.Assert.assertEquals;

/**
 * User : catherine
 */
@EnvTestTagsRequired(tags = "pytest")
public final class PythonPyTestingTest extends PyEnvTestCase {

  private final String myFrameworkName = PyTestFrameworkService.getSdkReadableNameByFramework(PyNames.PY_TEST);



  // Ensures setup/teardown does not break anything
  @Test
  public void testSetupTearDown() {
    runPythonTest(new SetupTearDownTestTask<PyTestTestProcessRunner>() {
      @NotNull
      @Override
      protected PyTestTestProcessRunner createProcessRunner() {
        return new PyTestTestProcessRunner("test_test.py", 1);
      }
    });
  }

  /**
   * Ensures that python target pointing to module works correctly
   */
  @Test
  public void testRunModuleAsFile() {
    runPythonTest(new RunModuleAsFileTask<PyTestTestProcessRunner>() {
      @NotNull
      @Override
      protected PyTestTestProcessRunner createProcessRunner() {
        return new PyTestTestProcessRunner(TARGET, 0);
      }
    });
  }


  @Test
  public void testRerunSubfolder() {
    runPythonTest(new RerunSubfolderTask<PyTestTestProcessRunner>(2) {
      @NotNull
      @Override
      protected PyTestTestProcessRunner createProcessRunner() {
        return new PyTestTestProcessRunner(".", 1);
      }
    });
  }


  @Test
  public void testParametrized() {
    runPythonTest(
      new PyProcessWithConsoleTestTask<PyTestTestProcessRunner>("/testRunner/env/pytest/parametrized", SdkCreationType.EMPTY_SDK) {

        @NotNull
        @Override
        protected PyTestTestProcessRunner createProcessRunner() {
          return new PyTestTestProcessRunner("test_pytest_parametrized.py", 1);
        }

        @Override
        protected void checkTestResults(@NotNull final PyTestTestProcessRunner runner,
                                        @NotNull final String stdout,
                                        @NotNull final String stderr,
                                        @NotNull final String all) {
          Assert.assertEquals("Parametrized test produced bad tree",
                              "Test tree:\n" +
                              "[root]\n" +
                              ".test_pytest_parametrized\n" +
                              "..test_eval\n" +
                              "...(three plus file-8)(-)\n" +
                              "...((2)+(4)-6)(+)\n" +
                              "...( six times nine_-42)(-)\n", runner.getFormattedTestTree());
        }
      });
  }


  /**
   * See https://github.com/JetBrains/teamcity-messages/issues/131
   */
  @Test
  public void testTestNameBeforeTestStarted() {
    runPythonTest(
      new PyProcessWithConsoleTestTask<PyTestTestProcessRunner>("/testRunner/env/pytest/testNameBeforeTestStarted", SdkCreationType.EMPTY_SDK) {

        @NotNull
        @Override
        protected PyTestTestProcessRunner createProcessRunner() {
          return new PyTestTestProcessRunner("test_test.py", 0);
        }

        @Override
        protected void checkTestResults(@NotNull final PyTestTestProcessRunner runner,
                                        @NotNull final String stdout,
                                        @NotNull final String stderr,
                                        @NotNull final String all) {
          Assert.assertEquals("Test name before message broke output",
                              "Test tree:\n" +
                              "[root]\n" +
                              ".test_test\n" +
                              "..SampleTest1\n" +
                              "...test_sample_1(+)\n" +
                              "...test_sample_2(+)\n" +
                              "...test_sample_3(+)\n" +
                              "...test_sample_4(+)\n" +
                              "..SampleTest2\n" +
                              "...test_sample_5(+)\n" +
                              "...test_sample_6(+)\n" +
                              "...test_sample_7(+)\n" +
                              "...test_sample_8(+)\n", runner.getFormattedTestTree());
        }
      });
  }


  // Ensure test survives patched strftime
  @Test
  public void testMonkeyPatch() {
    runPythonTest(
      new PyProcessWithConsoleTestTask<PyTestTestProcessRunner>("/testRunner/env/pytest/monkeyPatch", SdkCreationType.EMPTY_SDK) {

        @NotNull
        @Override
        protected PyTestTestProcessRunner createProcessRunner() {
          return new PyTestTestProcessRunner("test_test.py", 0);
        }

        @Override
        protected void checkTestResults(@NotNull PyTestTestProcessRunner runner,
                                        @NotNull String stdout,
                                        @NotNull String stderr,
                                        @NotNull String all) {
          assertEquals("Monkeypatch broke the test: " + stderr, 1, runner.getPassedTestsCount());
        }
      });
  }

  // Ensure slow test is not run when -m "not slow" is provided
  @Test
  public void testMarkerWithSpaces() {
    runPythonTest(
      new PyProcessWithConsoleTestTask<PyTestTestProcessRunner>("/testRunner/env/pytest/test_with_markers", SdkCreationType.EMPTY_SDK) {

        @NotNull
        @Override
        protected PyTestTestProcessRunner createProcessRunner() {
          return new PyTestTestProcessRunner("test_with_markers.py", 0) {
            @Override
            protected void configurationCreatedAndWillLaunch(@NotNull PyTestConfiguration configuration) throws IOException {
              super.configurationCreatedAndWillLaunch(configuration);
              configuration.setAdditionalArguments("-m 'not slow'");
            }
          };
        }


        @Override
        protected void checkTestResults(@NotNull PyTestTestProcessRunner runner,
                                        @NotNull String stdout,
                                        @NotNull String stderr,
                                        @NotNull String all) {
          Assert.assertEquals("Marker support broken", "Test tree:\n" +
                                                       "[root]\n" +
                                                       ".test_with_markers\n" +
                                                       "..test_fast(+)\n",
                              runner.getFormattedTestTree());
        }
      });
  }


  /**
   * New configuration should have closest src set as its working dir
   */
  @Test
  public void testClosestSrcIsWorkDirOnNewConfig() {
    runPythonTest(
      new CreateConfigurationTestTask<PyTestConfiguration>(myFrameworkName,
                                                           PyTestConfiguration.class) {
        @NotNull
        @Override
        protected List<PsiElement> getPsiElementsToRightClickOn() {
          configureSrcFolder(myFixture);

          myFixture.configureByFile("test_with_src/foo/src/test_test.py");
          final PyFunction test = myFixture.findElementByText("test_test", PyFunction.class);
          assert test != null;
          return Collections.singletonList(test);
        }

        @Override
        protected void checkConfiguration(@NotNull PyTestConfiguration configuration,
                                          @NotNull PsiElement elementToRightClickOn) {
          super.checkConfiguration(configuration, elementToRightClickOn);
          Assert
            .assertThat("Wrong configuration directory set on new config", configuration.getWorkingDirectory(), Matchers.endsWith("src"));
        }
      });
  }

  /**
   * In case when workdir is not set we should use closest src
   */
  @Test
  public void testClosestSrcIsWorkDirDynamically() {
    runPythonTest(
      new PyProcessWithConsoleTestTask<PyTestTestProcessRunner>("/testRunner/env/createConfigurationTest/", SdkCreationType.EMPTY_SDK) {
        @NotNull
        @Override
        protected PyTestTestProcessRunner createProcessRunner() {
          return new PyTestTestProcessRunner(TEST_TARGET_PREFIX + "test_test.test_test", 0) {
            @Override
            protected void configurationCreatedAndWillLaunch(@NotNull PyTestConfiguration configuration) throws IOException {
              super.configurationCreatedAndWillLaunch(configuration);
              // Reset dir to check it is calculated correctly
              configuration.setWorkingDirectory(null);
              configureSrcFolder(myFixture);
              Assert
                .assertThat("Wrong configuration directory calculated", configuration.getWorkingDirectorySafe(), Matchers.endsWith("src"));
            }
          };
        }


        @Override
        protected void checkTestResults(@NotNull final PyTestTestProcessRunner runner,
                                        @NotNull final String stdout,
                                        @NotNull final String stderr,
                                        @NotNull final String all) {
          Assert.assertEquals("Failed to run test" + stderr, 1, runner.getPassedTestsCount());
        }
      });
  }

  //TODO: DOC
  private static void configureSrcFolder(@NotNull final CodeInsightTestFixture fixture) {
    final ModuleRootManager manager = ModuleRootManager.getInstance(fixture.getModule());
    final ModifiableRootModel model = manager.getModifiableModel();
    final VirtualFile srcToMark = fixture.getTempDirFixture().getFile("test_with_src/foo/src");
    assert srcToMark != null;
    model.addContentEntry(srcToMark);
    model.commit();
  }

  @Test
  public void testConfigurationProducer() {
    runPythonTest(
      new CreateConfigurationByFileTask<>(myFrameworkName, PyTestConfiguration.class));
  }

  @Test
  public void testMultipleCases() {
    runPythonTest(
      new CreateConfigurationMultipleCasesTask<>(myFrameworkName, PyTestConfiguration.class));
  }

  /**
   * Checks tests are resolved when launched from subfolder
   */
  @Test
  public void testTestsInSubFolderResolvable() {
    runPythonTest(
      new PyTestsInSubFolderRunner<PyTestTestProcessRunner>("test_metheggs", "test_funeggs",
                                                            "test_first") {
        @NotNull
        @Override
        protected PyTestTestProcessRunner createProcessRunner() {
          return new PyTestTestProcessRunner(toFullPath("tests"), 0) {
            @Override
            protected void configurationCreatedAndWillLaunch(@NotNull PyTestConfiguration configuration) throws IOException {
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
      new PyTestsOutputRunner<PyTestTestProcessRunner>("test_metheggs", "test_funeggs", "test_first") {
        @NotNull
        @Override
        protected PyTestTestProcessRunner createProcessRunner() {
          return new PyTestTestProcessRunner(toFullPath("tests"), 0) {
            @Override
            protected void configurationCreatedAndWillLaunch(@NotNull PyTestConfiguration configuration) throws IOException {
              super.configurationCreatedAndWillLaunch(configuration);
              configuration.setWorkingDirectory(getWorkingFolderForScript());
            }
          };
        }
      });
  }

  @Test(expected = RuntimeConfigurationWarning.class)
  public void testValidation() {

    final CreateConfigurationTestTask.PyConfigurationCreationTask<PyTestConfiguration> task =
      new CreateConfigurationTestTask.PyConfigurationCreationTask<PyTestConfiguration>() {
        @NotNull
        @Override
        protected PyTestFactory createFactory() {
          return PyTestFactory.INSTANCE;
        }
      };
    runPythonTest(task);
    task.checkEmptyTarget();
  }

  @Test
  public void testConfigurationProducerOnDirectory() {
    runPythonTest(
      new CreateConfigurationByFileTask.CreateConfigurationTestAndRenameFolderTask<>(myFrameworkName,
                                                                                     PyTestConfiguration.class));
  }

  @Test
  public void testProduceConfigurationOnFile() {
    runPythonTest(
      new CreateConfigurationByFileTask<PyTestConfiguration>(myFrameworkName,
                                                             PyTestConfiguration.class, "spam.py") {
        @NotNull
        @Override
        protected PsiElement getElementToRightClickOnByFile(@NotNull final String fileName) {
          return myFixture.configureByFile(fileName);
        }
      });
  }

  @Test
  public void testRenameClass() {
    runPythonTest(
      new CreateConfigurationByFileTask.CreateConfigurationTestAndRenameClassTask<>(
        myFrameworkName,
        PyTestConfiguration.class));
  }

  /**
   * Ensure dots in test names do not break anything (PY-13833)
   */
  @Test
  public void testEscape() {
    runPythonTest(new PyProcessWithConsoleTestTask<PyTestTestProcessRunner>("/testRunner/env/pytest/", SdkCreationType.EMPTY_SDK) {
      @NotNull
      @Override
      protected PyTestTestProcessRunner createProcessRunner() {
        return new PyTestTestProcessRunner("test_escape_me.py", 0);
      }

      @Override
      protected void checkTestResults(@NotNull final PyTestTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        final String resultTree = runner.getFormattedTestTree().trim();
        final String expectedTree = myFixture.configureByFile("test_escape_me.tree.txt").getText().trim();
        Assert.assertEquals("Test result wrong tree", expectedTree, resultTree);
      }
    });
  }

  // Import error should lead to test failure
  @Test
  public void testFailInCaseOfError() {
    runPythonTest(new PyProcessWithConsoleTestTask<PyTestTestProcessRunner>("/testRunner/env/pytest/failTest", SdkCreationType.EMPTY_SDK) {

      @NotNull
      @Override
      protected PyTestTestProcessRunner createProcessRunner() {
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
   * Ensure element dir is used as curdir even if not set explicitly
   */
  @Test
  public void testCurrentDir() {
    runPythonTest(new PyProcessWithConsoleTestTask<PyTestTestProcessRunner>("/testRunner/env/pytest/", SdkCreationType.EMPTY_SDK) {
      @NotNull
      @Override
      protected PyTestTestProcessRunner createProcessRunner() {
        return new PyTestTestProcessRunner("", 0) {
          @Override
          protected void configurationCreatedAndWillLaunch(@NotNull final PyTestConfiguration configuration) throws IOException {
            super.configurationCreatedAndWillLaunch(configuration);
            configuration.setWorkingDirectory(null);
            final VirtualFile fullFilePath = myFixture.getTempDirFixture().getFile("dir_test.py");
            assert fullFilePath != null : String.format("No dir_test.py in %s", myFixture.getTempDirFixture().getTempDirPath());
            configuration.getTarget().setTarget(fullFilePath.getPath());
            configuration.getTarget().setTargetType(TestTargetType.PATH);
          }
        };
      }

      @Override
      protected void checkTestResults(@NotNull final PyTestTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        final String projectDir = myFixture.getTempDirFixture().getTempDirPath();
        Assert.assertThat("No directory found in output", runner.getConsole().getText(),
                          Matchers.containsString(String.format("Directory %s", PathUtil.toSystemDependentName(projectDir))));
      }
    });
  }

  @Test
  public void testPytestRunner() {

    runPythonTest(new PyProcessWithConsoleTestTask<PyTestTestProcessRunner>("/testRunner/env/pytest", SdkCreationType.EMPTY_SDK) {
      @NotNull
      @Override
      protected PyTestTestProcessRunner createProcessRunner() {
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

  /**
   * Ensure we can run path like "spam.bar" where "spam" is folder with out of init.py
   */
  @Test
  public void testPyTestFolderNoInitPy() {
    runPythonTest(new PyProcessWithConsoleTestTask<PyTestTestProcessRunner>("/testRunner/env/pytest", SdkCreationType.EMPTY_SDK) {
      @NotNull
      @Override
      protected PyTestTestProcessRunner createProcessRunner() {
        if (getLevelForSdk().isPy3K()) {
          return new PyTestTestProcessRunner("folder_no_init_py/test_test.py", 2);
        }
        else {
          return new PyTestTestProcessRunner(toFullPath("folder_no_init_py/test_test.py"), 2) {
            @Override
            protected void configurationCreatedAndWillLaunch(@NotNull PyTestConfiguration configuration) throws IOException {
              super.configurationCreatedAndWillLaunch(configuration);
              configuration.setWorkingDirectory(getWorkingFolderForScript());
            }
          };
        }
      }

      @Override
      protected void checkTestResults(@NotNull final PyTestTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        assertEquals(runner.getFormattedTestTree(), 1, runner.getFailedTestsCount());
        if (runner.getCurrentRerunStep() == 0) {
          assertEquals(runner.getFormattedTestTree(), 2, runner.getAllTestsCount());
          assertEquals(runner.getFormattedTestTree(), 1, runner.getPassedTestsCount());
        }
        else {
          assertEquals(runner.getFormattedTestTree(), 1, runner.getAllTestsCount());
          assertEquals(runner.getFormattedTestTree(), 0, runner.getPassedTestsCount());
        }
      }
    });
  }

  @Test
  public void testPytestRunner2() {
    runPythonTest(new PyProcessWithConsoleTestTask<PyTestTestProcessRunner>("/testRunner/env/pytest", SdkCreationType.EMPTY_SDK) {
      @NotNull
      @Override
      protected PyTestTestProcessRunner createProcessRunner() {
        return new PyTestTestProcessRunner("test2.py", 1);
      }

      @Override
      protected void checkTestResults(@NotNull final PyTestTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        if (runner.getCurrentRerunStep() > 0) {
          // We rerun all tests, since running parametrized tests is broken until
          // https://github.com/JetBrains/teamcity-messages/issues/121
          assertEquals(runner.getFormattedTestTree(), 7, runner.getAllTestsCount());
          assertEquals(runner.getFormattedTestTree(), 3, runner.getPassedTestsCount());
          assertEquals(runner.getFormattedTestTree(), 4, runner.getFailedTestsCount());
          return;
        }
        assertEquals(runner.getFormattedTestTree(), 9, runner.getAllTestsCount());
        assertEquals(runner.getFormattedTestTree(), 5, runner.getPassedTestsCount());
        assertEquals(runner.getFormattedTestTree(), 4, runner.getFailedTestsCount());
        // Py.test may report F before failed test, so we check string contains, not starts with
        Assert
          .assertThat("No test stdout", MockPrinter.fillPrinter(runner.findTestByName("testOne")).getStdOut(),
                      Matchers.containsString("I am test1"));

        // Ensure test has stdout even it fails
        final AbstractTestProxy testFail = runner.findTestByName("testFail");
        Assert.assertThat("No stdout for fail", MockPrinter.fillPrinter(testFail).getStdOut(),
                          Matchers.containsString("I will fail"));

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
      protected PyTestTestProcessRunner createProcessRunner() {
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
