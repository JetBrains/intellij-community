/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.jetbrains.env.Staging;
import com.jetbrains.env.ut.PyUnitTestProcessRunner;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.testing.PythonTestConfigurationsModel;
import com.jetbrains.python.testing.unittest.PythonUnitTestConfigurationProducer;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;

/**
 * @author traff
 */
public final class PythonUnitTestingTest extends PyEnvTestCase {

  @Test
  public void testConfigurationProducer() throws Exception {
    runPythonTest(
      new CreateConfigurationTestTask(PythonUnitTestConfigurationProducer.class, PythonTestConfigurationsModel.PYTHONS_UNITTEST_NAME));
  }

  @Test
  public void testUTRunner() {
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("/testRunner/env/unit", "test1.py") {


      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        assertEquals(2, runner.getAllTestsCount());
        assertEquals(2, runner.getPassedTestsCount());
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
   */
  @Test
  public void testRerunDerivedClass() throws Exception {
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("/testRunner/env/unit", "rerun_derived.py") {
      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        Assert.assertThat("Premature error", stderr, isEmptyString());
        Assert.assertThat("Wrong number of failed tests", runner.getFailedTestsCount(), equalTo(1));
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
        assertEquals(3, runner.getAllTestsCount());
        assertEquals(1, runner.getPassedTestsCount());
        assertEquals(2, runner.getFailedTestsCount());
      }
    });
  }

  /**
   * Ensures pattern is supported
   */
  @Test
  public void testUTRunnerByPattern() {
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("/testRunner/env/unit", "./_args_separator_*pattern.py") {


      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        assertEquals(4, runner.getAllTestsCount());
        assertEquals(2, runner.getPassedTestsCount());
        assertEquals(2, runner.getFailedTestsCount());
      }
    });
  }

  @Test
  public void testClass() {
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("/testRunner/env/unit", "test_file.py::GoodTest") {

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
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("/testRunner/env/unit", "test_file.py::GoodTest::test_passes") {

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
        Assert.assertThat("Bad line highlighted", fileNames, everyItem(endsWith(fileName)));
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
  @Staging
  public void testRelativeImports() {
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("/testRunner/env/unit/relativeImports", "relative_imports/tests/test_imps.py") {
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
}
