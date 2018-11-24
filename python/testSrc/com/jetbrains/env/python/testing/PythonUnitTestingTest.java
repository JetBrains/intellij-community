/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.env.python.testing;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.jetbrains.env.EnvTestTagsRequired;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.ut.PyUnitTestProcessRunner;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.sdk.InvalidSdkException;
import com.jetbrains.python.testing.PyUnitTestConfiguration;
import com.jetbrains.python.testing.PyUnitTestFactory;
import com.jetbrains.python.testing.PythonTestConfigurationsModel;
import com.jetbrains.python.testing.TestTargetType;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static com.jetbrains.env.ut.PyScriptTestProcessRunner.TEST_TARGET_PREFIX;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;

/**
 * @author traff
 */
public final class PythonUnitTestingTest extends PyEnvTestCase {


  /**
   * tests failfast as example of argument
   */
  @Test
  public void testFailFast() throws Exception {

    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("testRunner/env/unit/failFast", "test_test.py") {

      @NotNull
      @Override
      protected PyUnitTestProcessRunner createProcessRunner() throws Exception {
        return new PyUnitTestProcessRunner(toFullPath(myScriptName), 1) {
          @Override
          protected void configurationCreatedAndWillLaunch(@NotNull final PyUnitTestConfiguration configuration) throws IOException {
            super.configurationCreatedAndWillLaunch(configuration);
            configuration.setAdditionalArguments("-f"); //FailFast
          }
        };
      }

      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        Assert.assertEquals("Runner did not stop after first fail", 1, runner.getAllTestsCount());
        Assert.assertEquals("Bad tree produced for failfast", "Test tree:\n" +
                                                              "[root]\n" +
                                                              ".test_test\n" +
                                                              "..SomeTestCase\n" +
                                                              "...test_1_test(-)\n", runner.getFormattedTestTree());
      }
    });
  }


  /**
   *  check non-ascii (127+) chars are supported in skip messaged
   */
  @Test
  public void testNonAsciiMessage() throws Exception {

    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("testRunner/env/unit/nonAscii", "test_test.py") {


      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {

        runner.getFormattedTestTree();
        assertEquals("Skipped test with non-ascii message broke tree",
                     "Test tree:\n" +
                                                    "[root]\n" +
                                                    ".test_test\n" +
                                                    "..TestCase\n" +
                                                    "...test(~)\n", runner.getFormattedTestTree());
        Assert.assertThat("non-ascii char broken in output", stdout, containsString("ошибка"));
      }
    });
  }


  /**
   * tests with docstrings are reported as "test.name (text)" by unittest.
   */
  @Test
  public void testWithDocString() throws Exception {

    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("testRunner/env/unit/withDocString", "test_test.py") {

      @NotNull
      @Override
      protected PyUnitTestProcessRunner createProcessRunner() throws Exception {
        return new PyUnitTestProcessRunner(toFullPath(myScriptName), 1);
      }

      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        if (runner.getCurrentRerunStep() == 0) {
          assertEquals("test with docstring produced bad tree", "Test tree:\n" +
                                                                "[root]\n" +
                                                                ".test_test\n" +
                                                                "..SomeTestCase\n" +
                                                                "...testSomething (Only with docstring test is parsed with extra space)(+)\n" +
                                                                "...testSomethingBad (Fail)(-)\n", runner.getFormattedTestTree());
        }
        else {
          assertEquals("test with docstring failed to rerun",
                       "Test tree:\n" +
                       "[root]\n" +
                       ".test_test\n" +
                       "..SomeTestCase\n" +
                       "...testSomethingBad (Fail)(-)\n", runner.getFormattedTestTree());
        }
      }
    });
  }


  // Ensure failed and error subtests work
  @Test
  @EnvTestTagsRequired(tags = "python3")
  public void testSubTestError() throws Exception {
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("testRunner/env/unit/subtestError", "test_test.py") {

      @NotNull
      @Override
      protected PyUnitTestProcessRunner createProcessRunner() throws Exception {
        return new PyUnitTestProcessRunner(toFullPath(myScriptName), 1);
      }

      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        assertEquals("subtest error reported as success", "Test tree:\n" +
                                                          "[root]\n" +
                                                          ".test_test\n" +
                                                          "..TestThis\n" +
                                                          "...test_this\n" +
                                                          "....[test](-)\n", runner.getFormattedTestTree());
      }
    });
  }


  /**
   * subtest names may have dots and shall not break test tree
   */
  @EnvTestTagsRequired(tags = "python3")
  @Test
  public void testDotsInSubtest() throws Exception {
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("testRunner/env/unit/subtestDots", "test_test.py") {

      @NotNull
      @Override
      protected PyUnitTestProcessRunner createProcessRunner() throws Exception {
        return new PyUnitTestProcessRunner(toFullPath(myScriptName), 1);
      }

      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        assertEquals("dots in subtest names broke output", "Test tree:\n" +
                                                           "[root]\n" +
                                                           ".test_test\n" +
                                                           "..SampleTest\n" +
                                                           "...test_sample\n" +
                                                           "....(i='0_0')(-)\n" +
                                                           "....(i='1_1')(-)\n" +
                                                           "....(i='2_2')(+)\n" +
                                                           "....(i='3_3')(+)\n" +
                                                           "....(i='4_4')(+)\n" +
                                                           "....(i='5_5')(+)\n" +
                                                           "....(i='6_6')(+)\n" +
                                                           "....(i='7_7')(+)\n" +
                                                           "....(i='8_8')(+)\n" +
                                                           "....(i='9_9')(+)\n", runner.getFormattedTestTree());
      }
    });
  }

  @EnvTestTagsRequired(tags = "unittest2")
  @Test
  public void testUnitTest2() throws Exception {
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("testRunner/env/unit/unittest2", "test_test.py") {

      @NotNull
      @Override
      protected PyUnitTestProcessRunner createProcessRunner() throws Exception {
        return new PyUnitTestProcessRunner(toFullPath(myScriptName), 1);
      }

      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        runner.getFormattedTestTree();
        assertEquals("unittest2 produced wrong tree", "Test tree:\n" +
                                                      "[root]\n" +
                                                      ".test_test\n" +
                                                      "..SampleTest\n" +
                                                      "...test_sample\n" +
                                                      "....(i=0)(-)\n" +
                                                      "....(i=1)(-)\n" +
                                                      "....(i=2)(-)\n" +
                                                      "....(i=3)(-)\n" +
                                                      "....(i=4)(+)\n" +
                                                      "....(i=5)(+)\n" +
                                                      "....(i=6)(+)\n" +
                                                      "....(i=7)(+)\n" +
                                                      "....(i=8)(+)\n" +
                                                      "....(i=9)(+)\n", runner.getFormattedTestTree());
      }
    });
  }

  // Ensures setup/teardown does not break anything
  @Test
  public void testSetupTearDown() throws Exception {
    runPythonTest(new SetupTearDownTestTask<PyUnitTestProcessRunner>() {
      @NotNull
      @Override
      protected PyUnitTestProcessRunner createProcessRunner() throws Exception {
        return new PyUnitTestProcessRunner("test_test.py", 1);
      }
    });
  }


  /**
   * Raising SkipTest on class setup should not lead to KeyError
   */
  @Test
  public void testSkipInSetup() throws Exception {
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("testRunner/env/unit/skipInSetup", "test_test.py") {


      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        Assert.assertEquals("Output tree broken for skipped exception thrown in setup method" ,"Test tree:\n" +
                                "[root]\n" +
                                ".test_test\n" +
                                "..TestSimple\n" +
                                "...setUpClass(~)\n" +
                                "..TestSubSimple\n" +
                                "...setUpClass(~)\n", runner.getFormattedTestTree());
      }
    });
  }


  /**
   * Ensure that sys.path[0] is script folder, not helpers folder
   */
  @Test
  public void testSysPath() throws Exception {
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("testRunner/env/unit/sysPath", "test_sample.py") {

      @NotNull
      @Override
      protected PyUnitTestProcessRunner createProcessRunner() throws Exception {
        return new PyUnitTestProcessRunner(toFullPath(myScriptName), 0);
      }

      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        Assert.assertEquals(runner.getFormattedTestTree(), 1, runner.getAllTestsCount());
        myFixture.getTempDirFixture().getFile("sysPath");

        final VirtualFile folderWithScript = myFixture.getTempDirFixture().getFile(".");
        assert folderWithScript != null : "No folder for script " + myScriptName;
        Assert.assertThat("sys.path[0] should point to folder with test, while it does not", stdout,
                          Matchers.containsString(String.format("path[0]=%s", new File(folderWithScript.getPath()).getAbsolutePath())));
      }
    });
  }


  /**
   * Make sure test rerun works when pattern is enabled (PY-23416)
   */
  @Test
  @EnvTestTagsRequired(tags = "python3")
  public void testScriptWithHyphen() throws Exception {
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("testRunner/env/unit/withHyphen", "test-foobar.py") {

      @NotNull
      @Override
      protected PyUnitTestProcessRunner createProcessRunner() throws Exception {
        return new PyUnitTestProcessRunner(toFullPath(myScriptName), 1);
      }

      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        if (runner.getCurrentRerunStep() == 0) {
          Assert.assertEquals(runner.getFormattedTestTree(), 2, runner.getAllTestsCount());
          Assert.assertEquals(runner.getFormattedTestTree(), 1, runner.getPassedTestsCount());
          Assert.assertEquals(runner.getFormattedTestTree(), 1, runner.getFailedTestsCount());
        }
        else {
          Assert.assertEquals(runner.getFormattedTestTree(), 1, runner.getAllTestsCount());
          Assert.assertEquals(runner.getFormattedTestTree(), 0, runner.getPassedTestsCount());
          Assert.assertEquals(runner.getFormattedTestTree(), 1, runner.getFailedTestsCount());
        }
      }
    });
  }


  /**
   * Make sure test rerun works when pattern is enabled (PY-23416)
   */
  @Test
  public void testPatternRerun() throws Exception {
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("testRunner/env/unit/patternRerun", ".") {

      @NotNull
      @Override
      protected PyUnitTestProcessRunner createProcessRunner() throws Exception {
        // Full pass is required because it is folder
        return new PyUnitTestProcessRunner(toFullPath(myScriptName), 2) {
          @Override
          protected void configurationCreatedAndWillLaunch(@NotNull final PyUnitTestConfiguration configuration)
            throws IOException {
            super.configurationCreatedAndWillLaunch(configuration);
            configuration.setPattern("test*");
          }
        };
      }

      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        if (runner.getCurrentRerunStep() == 0) {
          Assert.assertEquals(stderr, 2, runner.getAllTestsCount());
        }
        else {
          Assert.assertEquals(stderr, 1, runner.getAllTestsCount());
        }
      }
    });
  }

  /**
   * Ensures that python target pointing to module works correctly
   */
  @Test
  public void testRunModuleAsFile() throws Exception {
    runPythonTest(new RunModuleAsFileTask<PyUnitTestProcessRunner>() {
      @NotNull
      @Override
      protected PyUnitTestProcessRunner createProcessRunner() throws Exception {
        return new PyUnitTestProcessRunner(TARGET, 0);
      }
    });
  }

  @Test
  public void testRerunSubfolder() throws Exception {
    runPythonTest(new RerunSubfolderTask<PyUnitTestProcessRunner>(1) {
      @NotNull
      @Override
      protected PyUnitTestProcessRunner createProcessRunner() throws Exception {
        return new PyUnitTestProcessRunner(".", 1) {
          @Override
          protected void configurationCreatedAndWillLaunch(@NotNull PyUnitTestConfiguration configuration) throws IOException {
            super.configurationCreatedAndWillLaunch(configuration);
            // Unittest can't find tests in folders with out of init.py, even in py2k, so we set working dir explicitly
            configuration.setWorkingDirectory(toFullPath("tests"));
          }
        };
      }
    });
  }


  @EnvTestTagsRequired(tags = "python3") // Rerun for this scenario does not work for unitttest and py2
  //https://github.com/JetBrains/teamcity-messages/issues/129
  @Test
  public void testPackageInsideFolder() throws Exception {
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("testRunner/env/unit/package_in_folder", "tests") {

      @NotNull
      @Override
      protected PyUnitTestProcessRunner createProcessRunner() throws Exception {
        // Full pass is required because it is folder
        return new PyUnitTestProcessRunner(toFullPath(myScriptName), 2) {
          @Override
          protected void configurationCreatedAndWillLaunch(@NotNull PyUnitTestConfiguration configuration) throws IOException {
            super.configurationCreatedAndWillLaunch(configuration);
            configuration.setWorkingDirectory(null); //Unset working dir: should be set to tests automatically
          }
        };
      }

      @Override
      protected void checkTestResults(@NotNull PyUnitTestProcessRunner runner,
                                      @NotNull String stdout,
                                      @NotNull String stderr,
                                      @NotNull String all) {
        if (runner.getCurrentRerunStep() == 0) {
          Assert.assertEquals(runner.getFormattedTestTree(), 2, runner.getAllTestsCount());
        }
        else {
          Assert.assertEquals(runner.getFormattedTestTree(), 1, runner.getAllTestsCount());
        }
      }
    });
  }

  @EnvTestTagsRequired(tags = "python3") // No subtest in py2
  @Test
  public void testSubtest() throws Exception {
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("testRunner/env/unit/", "test_subtest.py", 1) {
      @Override
      protected void checkTestResults(@NotNull PyUnitTestProcessRunner runner,
                                      @NotNull String stdout,
                                      @NotNull String stderr,
                                      @NotNull String all) {
        final String expectedResult = "Test tree:\n" +
                                      "[root]\n" +
                                      ".test_subtest\n" +
                                      "..SpamTest\n" +
                                      "...test_test\n" +
                                      "....(i=0)(-)\n" +
                                      "....(i=1)(+)\n" +
                                      "....(i=2)(-)\n" +
                                      "....(i=3)(+)\n" +
                                      "....(i=4)(-)\n" +
                                      "....(i=5)(+)\n" +
                                      "....(i=6)(-)\n" +
                                      "....(i=7)(+)\n" +
                                      "....(i=8)(-)\n" +
                                      "....(i=9)(+)\n";
        Assert.assertEquals("", expectedResult, runner.getFormattedTestTree());
      }
    });
  }

  @EnvTestTagsRequired(tags = "python3") // No subtest in py2
  @Test
  public void testSubtestSkipped() throws Exception {
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("testRunner/env/unit/", "test_skipped_subtest.py", 1) {
      @Override
      protected void checkTestResults(@NotNull PyUnitTestProcessRunner runner,
                                      @NotNull String stdout,
                                      @NotNull String stderr,
                                      @NotNull String all) {
        Assert.assertEquals(runner.getFormattedTestTree(), 8, runner.getPassedTestsCount());
        Assert.assertEquals(runner.getFormattedTestTree(), 2, runner.getIgnoredTestsCount());
      }
    });
  }

  @Test
  public void testMultipleCases() throws Exception {
    runPythonTest(
      new CreateConfigurationMultipleCasesTask<PyUnitTestConfiguration>(PythonTestConfigurationsModel.PYTHONS_UNITTEST_NAME,
                                                 PyUnitTestConfiguration.class){
        @Override
        protected boolean configurationShouldBeProducedForElement(@NotNull final PsiElement element) {
          // test_functions.py does not conttain any TestCase and can't be launched with unittest
          final PsiFile file = element.getContainingFile();
          return file == null || ! file.getName().endsWith("test_functions.py");
        }
      });
  }

  /**
   * Ensures newly created configuration inherits working dir from default if set
   */
  @Test
  public void testConfigurationProducerObeysDefaultDir() throws Exception {
    runPythonTest(
      new CreateConfigurationByFileTask<PyUnitTestConfiguration>(PythonTestConfigurationsModel.PYTHONS_UNITTEST_NAME,
                                                                 PyUnitTestConfiguration.class) {
        private static final String SOME_RANDOM_DIR = "//some/random/ddir";

        @Override
        public void runTestOn(final String sdkHome) throws InvalidSdkException, IOException {
          // Set default working directory to some random location before actual exection
          final PyUnitTestConfiguration templateConfiguration = getTemplateConfiguration(PyUnitTestFactory.INSTANCE);
          templateConfiguration.setWorkingDirectory(SOME_RANDOM_DIR);
          super.runTestOn(sdkHome);
          templateConfiguration.setWorkingDirectory("");
        }

        @Override
        protected void checkConfiguration(@NotNull final PyUnitTestConfiguration configuration,
                                          @NotNull final PsiElement elementToRightClickOn) {
          super.checkConfiguration(configuration, elementToRightClickOn);
          Assert.assertEquals("UnitTest does not obey default working directory", SOME_RANDOM_DIR,
                              configuration.getWorkingDirectorySafe());
        }
      }
    );
  }

  // PY-24407
  @Test
  public void testWorkingDirectoryDependsOnRelativeImport() throws Exception {
    runPythonTest(new CreateConfigurationTestTask<PyUnitTestConfiguration>(PythonTestConfigurationsModel.PYTHONS_UNITTEST_NAME, PyUnitTestConfiguration.class){
      @NotNull
      @Override
      protected List<PsiElement> getPsiElementsToRightClickOn() {
        myFixture.configureByFile("testRelativeImport/src/tests/test_no_relative.py");
        final PyFunction noRelativeImportFun = myFixture.findElementByText("test_no_relative", PyFunction.class);
        assert noRelativeImportFun != null;

        myFixture.configureByFile("testRelativeImport/src/tests/test_relative.py");
        final PyFunction relativeImportFun = myFixture.findElementByText("test_relative", PyFunction.class);
        assert relativeImportFun != null;


        return Arrays.asList(relativeImportFun, noRelativeImportFun);
      }

      @Override
      protected void checkConfiguration(@NotNull PyUnitTestConfiguration configuration, @NotNull PsiElement elementToRightClickOn) {
        super.checkConfiguration(configuration, elementToRightClickOn);
        configuration.getWorkingDirectorySafe();

        final PyFunction function = (PyFunction)elementToRightClickOn;
        if (function.getName().equals("test_relative")) {
          Assert.assertThat("Wrong dir  for relative import", configuration.getWorkingDirectory(), endsWith("testRelativeImport"));
          assertEquals("Bad target", "src.tests.test_relative.ModuleTest.test_relative", configuration.getTarget().getTarget());
        } else if (function.getName().equals("test_no_relative")) {
          Assert.assertThat("Wrong dir for non relative import", configuration.getWorkingDirectory(), endsWith("testRelativeImport/src/tests"));
          assertEquals("Bad target", "test_no_relative.ModuleTest.test_no_relative", configuration.getTarget().getTarget());
        } else {
          throw new AssertionError("Unexpected function " + function.getName());
        }
      }
    });
  }

  @Test
  public void testConfigurationProducer() throws Exception {
    runPythonTest(new CreateConfigurationByFileTask<>(PythonTestConfigurationsModel.PYTHONS_UNITTEST_NAME, PyUnitTestConfiguration.class));
  }

  /**
   * Checks tests are resolved when launched from subfolder
   */
  @Test
  public void testTestsInSubFolderResolvable() throws Exception {
    runPythonTest(
      new PyUnitTestProcessWithConsoleTestTask.PyTestsInSubFolderRunner<PyUnitTestProcessRunner>("test_metheggs", "test_first") {
        @NotNull
        @Override
        protected PyUnitTestProcessRunner createProcessRunner() throws Exception {
          return new PyUnitTestProcessRunner(toFullPath("tests"), 0) {
            @Override
            protected void configurationCreatedAndWillLaunch(@NotNull PyUnitTestConfiguration configuration) throws IOException {
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
      new PyUnitTestProcessWithConsoleTestTask.PyTestsOutputRunner<PyUnitTestProcessRunner>("test_metheggs", "test_first") {
        @NotNull
        @Override
        protected PyUnitTestProcessRunner createProcessRunner() throws Exception {
          return new PyUnitTestProcessRunner(toFullPath("tests"), 0) {
            @Override
            protected void configurationCreatedAndWillLaunch(@NotNull PyUnitTestConfiguration configuration) throws IOException {
              super.configurationCreatedAndWillLaunch(configuration);
              configuration.setWorkingDirectory(getWorkingFolderForScript());
            }
          };
        }
      });
  }


  @Test(expected = RuntimeConfigurationWarning.class)
  public void testValidation() throws Exception {

    final CreateConfigurationTestTask.PyConfigurationCreationTask<PyUnitTestConfiguration> task =
      new CreateConfigurationTestTask.PyConfigurationCreationTask<PyUnitTestConfiguration>() {
        @NotNull
        @Override
        protected PyUnitTestFactory createFactory() {
          return PyUnitTestFactory.INSTANCE;
        }
      };
    runPythonTest(task);
    final PyUnitTestConfiguration configuration = task.getConfiguration();
    configuration.setPattern("foo");
    configuration.getTarget().setTargetType(TestTargetType.PATH);
    configuration.getTarget().setTarget("foo.py");
    configuration.checkConfiguration();
  }


  @Test
  public void testConfigurationProducerOnDirectory() throws Exception {
    runPythonTest(
      new CreateConfigurationByFileTask.CreateConfigurationTestAndRenameFolderTask<>(PythonTestConfigurationsModel.PYTHONS_UNITTEST_NAME,
                                                                                     PyUnitTestConfiguration.class));
  }

  @Test
  public void testRenameClass() throws Exception {
    runPythonTest(
      new CreateConfigurationByFileTask.CreateConfigurationTestAndRenameClassTask<>(
        PythonTestConfigurationsModel.PYTHONS_UNITTEST_NAME,
        PyUnitTestConfiguration.class));
  }

  @Test
  public void testUTRunner() {
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("/testRunner/env/unit", "test1.py") {


      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        assertEquals(runner.getFormattedTestTree(), 2, runner.getAllTestsCount());
        assertEquals(runner.getFormattedTestTree(), 2, runner.getPassedTestsCount());
        runner.assertAllTestsPassed();
      }
    });
  }

  /**
   * Checks <a href="https://docs.python.org/2/library/unittest.html#load-tests-protocol">Load test protocol</a>
   */
  @Test
  public void testLoadProtocol() throws Exception {
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("/testRunner/env/unit", "test_load_protocol.py") {
      @Override
      public boolean isLanguageLevelSupported(@NotNull final LanguageLevel level) {
        // "load_protocol" does not exist before 2.7
        return level.compareTo(LanguageLevel.PYTHON26) > 0;
      }

      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        assertEquals("bad num of passed tests: unittest load protocol failed to find tests?", 3, runner.getPassedTestsCount());
        runner.assertAllTestsPassed();
      }
    });
  }

  /**
   * Ensure rerun test works even if test is declared in parent
   * See https://github.com/JetBrains/teamcity-messages/issues/117
   */
  @Test
  public void testRerunDerivedClass() throws Exception {
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("/testRunner/env/unit", "rerun_derived.py") {
      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        Assert.assertThat("Wrong number of failed tests", runner.getFailedTestsCount(), equalTo(1));
        final int expectedNumberOfTests = (runner.getCurrentRerunStep() == 0 ? 2 : 1);
        Assert.assertThat("Wrong number tests", runner.getAllTestsCount(), equalTo(expectedNumberOfTests));
        if (runner.getCurrentRerunStep() == 1) {
          // Make sure derived tests are launched, not abstract
          Assert.assertEquals("Wrong tests after rerun",
                              "Test tree:\n" +
                              "[root]\n" +
                              ".rerun_derived\n" +
                              "..TestDerived\n" +
                              "...test_a(-)\n", runner.getFormattedTestTree());
        }
      }

      @NotNull
      @Override
      protected PyUnitTestProcessRunner createProcessRunner() throws Exception {
        return new PyUnitTestProcessRunner(myScriptName, 2);
      }
    });
  }

  /**
   * Run tests, delete file and click "rerun" should throw exception and display error since test ids do not point to correct PSI
   * from that moment
   */
  @Test
  public void testCantRerun() throws Exception {
    startMessagesCapture();

    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("/testRunner/env/unit", "test_with_skips_and_errors.py") {

      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        assert runner.getFailedTestsCount() > 0 : "We need failed tests to test broken rerun";

        startMessagesCapture();

        EdtTestUtil.runInEdtAndWait(() -> {
          deleteAllTestFiles(myFixture);
          runner.rerunFailedTests();
        });

        final List<Throwable> throwables = getCapturesMessages().first;
        Assert.assertThat("Exception shall be thrown", throwables, not(emptyCollectionOf(Throwable.class)));
        final Throwable exception = throwables.get(0);
        Assert.assertThat("ExecutionException should be thrown", exception, instanceOf(ExecutionException.class));
        Assert.assertThat("Wrong text", exception.getMessage(), equalTo(PyBundle.message("runcfg.tests.cant_rerun")));
        Assert.assertThat("No messages displayed for exception", getCapturesMessages().second, not(emptyCollectionOf(String.class)));


        stopMessageCapture();
      }
    });
  }

  /**
   * Ensures that skipped and erroneous tests do not lead to suite ignorance
   */
  @Test
  public void testUTSkippedAndIgnored() {
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("/testRunner/env/unit", "test_with_skips_and_errors.py") {

      @Override
      public boolean isLanguageLevelSupported(@NotNull final LanguageLevel level) {
        // This test requires unittest to have decorator "test" that does not exists in 2.6
        return level.compareTo(LanguageLevel.PYTHON26) > 0;
      }

      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        assertEquals(4, runner.getAllTestsCount());
        assertEquals(2, runner.getPassedTestsCount());
        assertEquals(2, runner.getFailedTestsCount());
        Assert.assertFalse("Suite is not finished", runner.getTestProxy().isInterrupted());
      }
    });
  }

  @Test
  public void testUTRunner2() {
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("/testRunner/env/unit", "test2.py") {

      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        assertEquals(runner.getFormattedTestTree(), 3, runner.getAllTestsCount());
        assertEquals(runner.getFormattedTestTree(), 1, runner.getPassedTestsCount());
        assertEquals(runner.getFormattedTestTree(), 2, runner.getFailedTestsCount());
      }
    });
  }

  /**
   * Ensures pattern is supported
   */
  @Test
  public void testUTRunnerByPattern() {
    runPythonTest(
      new PyUnitTestProcessWithConsoleTestTask("/testRunner/env/unit", PyUnitTestProcessRunner.TEST_PATTERN_PREFIX + "*pattern.py") {


        @Override
        protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                        @NotNull final String stdout,
                                        @NotNull final String stderr,
                                        @NotNull final String all) {
          assertEquals(runner.getFormattedTestTree(), 4, runner.getAllTestsCount());
          assertEquals(runner.getFormattedTestTree(), 2, runner.getPassedTestsCount());
          assertEquals(runner.getFormattedTestTree(), 2, runner.getFailedTestsCount());
        }
      });
  }

  /**
   * Ensure "[" in test does not break output
   */
  @Test
  public void testEscaping() throws Exception {
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("/testRunner/env/unit", "test_escaping.py") {


      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        assertEquals(1, runner.getAllTestsCount());
        assertEquals(0, runner.getPassedTestsCount());
        assertEquals(1, runner.getFailedTestsCount());
      }
    });
  }

  @Test
  public void testClass() {
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("/testRunner/env/unit",
                                                           TEST_TARGET_PREFIX + "test_file.GoodTest") {

      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
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
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("/testRunner/env/unit",
                                                           TEST_TARGET_PREFIX +
                                                           "test_file.GoodTest.test_passes") {

      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        assertEquals(1, runner.getAllTestsCount());
        assertEquals(1, runner.getPassedTestsCount());
      }
    });
  }

  @Test
  public void testFolder() {
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("/testRunner/env/unit", "test_folder/") {

      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        assertEquals(5, runner.getAllTestsCount());
        assertEquals(3, runner.getPassedTestsCount());
      }
    });
  }

  /**
   * Ensures file references are highlighted for python traceback
   */
  @Test
  public void testUnitTestFileReferences() {
    final String fileName = "reference_tests.py";
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("/testRunner/env/unit", fileName) {

      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        final List<String> fileNames = runner.getHighlightedStringsInConsole().getSecond();
        Assert.assertTrue(String.format("Not enough highlighted entries(%s) in the following output: %s",
                                        StringUtil.join(fileNames, ","),
                                        runner.getAllConsoleText()),
                          fileNames.size() >= 3);
        // UnitTest highlights file name
        Assert.assertThat("Bad line highlighted", fileNames, hasItem(endsWith(fileName)));
      }
    });
  }

  @Test
  public void testDependent() {
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("/testRunner/env/unit", "dependentTests/test_my_class.py") {

      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        assertEquals(1, runner.getAllTestsCount());
        assertEquals(1, runner.getPassedTestsCount());
      }
    });
  }

  @Test
  public void testRelativeImports() {
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("/testRunner/env/unit/relativeImports",
                                                           PyUnitTestProcessRunner.TEST_PATTERN_PREFIX + "test_imps.py") {
      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        assertEquals(runner.getFormattedTestTree(), 1, runner.getAllTestsCount());
        assertEquals(runner.getFormattedTestTree(), 1, runner.getPassedTestsCount());
      }
    });
  }

  /**
   * Deletes all files in temp. folder
   */
  private static void deleteAllTestFiles(@NotNull final CodeInsightTestFixture fixture) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      final VirtualFile testRoot = fixture.getTempDirFixture().getFile(".");
      assert testRoot != null : "No temp path?";
      try {
        for (final VirtualFile child : testRoot.getChildren()) {
          child.delete(null);
        }
      }
      catch (final IOException e) {
        throw new AssertionError(String.format("Failed to delete files in  %s : %s", testRoot, e));
      }
    });
  }
}
