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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.ut.PyScriptTestProcessRunner;
import com.jetbrains.env.ut.PyUnitTestProcessRunner;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.LanguageLevel;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.jetbrains.env.ut.PyScriptTestProcessRunner.TEST_TARGET_PREFIX;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;

/**
 * Parent of unittest and trial test.
 * All tests here should run with trial and unit
 */
public abstract class PythonUnitTestingLikeTest<T extends PyScriptTestProcessRunner<?>> extends PyEnvTestCase {
  /**
   * Ensure "[" in test does not break output
   */
  @Test
  public void testEscaping() throws Exception {
    runPythonTest(new PyUnitTestLikeProcessWithConsoleTestTask<T>("/testRunner/env/unit", "test_escaping.py", this::createTestRunner) {


      @Override
      protected void checkTestResults(@NotNull final T runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        assertEquals(1, runner.getAllTestsCount());
        assertEquals(0, runner.getPassedTestsCount());
        assertEquals(1, runner.getFailedTestsCount());
      }
    });
  }

  abstract T createTestRunner(@NotNull final TestRunnerConfig config);

  /**
   * Ensure that sys.path[0] is script folder, not helpers folder
   */
  @Test
  public void testSysPath() throws Exception {
    runPythonTest(new PyUnitTestLikeProcessWithConsoleTestTask<T>("testRunner/env/unit/sysPath", "test_sample.py", this::createTestRunner) {

      @NotNull
      @Override
      protected T createProcessRunner() throws Exception {
        return getProcessRunnerCreator().apply(new TestRunnerConfig(toFullPath(getMyScriptName()), 0));
      }

      @Override
      protected void checkTestResults(@NotNull final T runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        Assert.assertEquals(runner.getFormattedTestTree(), 1, runner.getAllTestsCount());
        myFixture.getTempDirFixture().getFile("sysPath");

        final VirtualFile folderWithScript = myFixture.getTempDirFixture().getFile(".");
        assert folderWithScript != null : "No folder for script " + getMyScriptName();
        Assert.assertThat("sys.path[0] should point to folder with test, while it does not", stdout,
                          Matchers.containsString(String.format("path[0]=%s", new File(folderWithScript.getPath()).getAbsolutePath())));
      }
    });
  }

  @Test
  public void testUTRunner() {
    runPythonTest(new PyUnitTestLikeProcessWithConsoleTestTask<T>("/testRunner/env/unit", "test1.py", this::createTestRunner) {


      @Override
      protected void checkTestResults(@NotNull final T runner,
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
   * tests with docstrings are reported as "test.name (text)" by unittest.
   */
  @Test
  public void testWithDocString() throws Exception {

    runPythonTest(
      new PyUnitTestLikeProcessWithConsoleTestTask<T>("testRunner/env/unit/withDocString", "test_test.py", this::createTestRunner) {

        @NotNull
        @Override
        protected T createProcessRunner() throws Exception {
          return getProcessRunnerCreator().apply(new TestRunnerConfig(toFullPath(getMyScriptName()), 1));
        }

        @Override
        protected void checkTestResults(@NotNull final T runner,
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

  @Test
  public void testClass() {
    runPythonTest(new PyUnitTestLikeProcessWithConsoleTestTask<T>("/testRunner/env/unit",
                                                                  TEST_TARGET_PREFIX + "test_file.GoodTest", this::createTestRunner) {

      @Override
      protected void checkTestResults(@NotNull final T runner,
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
    runPythonTest(new PyUnitTestLikeProcessWithConsoleTestTask<T>("/testRunner/env/unit", "test2.py", this::createTestRunner) {

      @Override
      protected void checkTestResults(@NotNull final T runner,
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
   * Ensures file references are highlighted for python traceback
   */
  @Test
  public void testUnitTestFileReferences() {
    final String fileName = "reference_tests.py";
    runPythonTest(new PyUnitTestLikeProcessWithConsoleTestTask<T>("/testRunner/env/unit", fileName, this::createTestRunner) {

      @Override
      protected void checkTestResults(@NotNull final T runner,
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

  /**
   * Ensures that skipped and erroneous tests do not lead to suite ignorance
   */
  @Test
  public void testUTSkippedAndIgnored() {
    runPythonTest(
      new PyUnitTestLikeProcessWithConsoleTestTask<T>("/testRunner/env/unit", "test_with_skips_and_errors.py", this::createTestRunner) {

        @Override
        public boolean isLanguageLevelSupported(@NotNull final LanguageLevel level) {
          // This test requires unittest to have decorator "test" that does not exists in 2.6
          return level.compareTo(LanguageLevel.PYTHON26) > 0;
        }

        @Override
        protected void checkTestResults(@NotNull final T runner,
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
  public void testDependent() {
    runPythonTest(
      new PyUnitTestLikeProcessWithConsoleTestTask<T>("/testRunner/env/unit", "dependentTests/test_my_class.py", this::createTestRunner) {

        @Override
        protected void checkTestResults(@NotNull final T runner,
                                        @NotNull final String stdout,
                                        @NotNull final String stderr,
                                        @NotNull final String all) {
          assertEquals(1, runner.getAllTestsCount());
          assertEquals(1, runner.getPassedTestsCount());
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

  /**
   * Ensure rerun test works even if test is declared in parent
   * See https://github.com/JetBrains/teamcity-messages/issues/117
   */
  @Test
  public void testRerunDerivedClass() throws Exception {
    runPythonTest(new PyUnitTestLikeProcessWithConsoleTestTask<T>("/testRunner/env/unit", "rerun_derived.py", this::createTestRunner) {
      @Override
      protected void checkTestResults(@NotNull final T runner,
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
      protected T createProcessRunner() throws Exception {
        return getProcessRunnerCreator().apply(new TestRunnerConfig(getMyScriptName(), 2));
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


  @Test
  public void testFolder() {
    runPythonTest(new PyUnitTestLikeProcessWithConsoleTestTask<T>("/testRunner/env/unit", "test_folder/", this::createTestRunner) {

      @Override
      protected void checkTestResults(@NotNull final T runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        assertEquals(5, runner.getAllTestsCount());
        assertEquals(3, runner.getPassedTestsCount());
      }
    });
  }
}
