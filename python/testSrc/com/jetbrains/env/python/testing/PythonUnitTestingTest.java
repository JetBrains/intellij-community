package com.jetbrains.env.python.testing;

import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.ut.PyUnitTestProcessRunner;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.testing.PythonTestConfigurationsModel;
import com.jetbrains.python.testing.unittest.PythonUnitTestConfigurationProducer;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.List;

/**
 * @author traff
 */
public final class PythonUnitTestingTest extends PyEnvTestCase {

  public void testConfigurationProducer() throws Exception {
    runPythonTest(
      new CreateConfigurationTestTask(PythonUnitTestConfigurationProducer.class, PythonTestConfigurationsModel.PYTHONS_UNITTEST_NAME));
  }
  public void testUTRunner() {
    runPythonTest(new PyUnitTestProcessWithConsoleTestTask("/testRunner/env/unit", "test1.py") {


      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        Assert.assertEquals(2, runner.getAllTestsCount());
        Assert.assertEquals(2, runner.getPassedTestsCount());
        runner.assertAllTestsPassed();
      }
    });
  }

  /**
   * Checks <a href="https://docs.python.org/2/library/unittest.html#load-tests-protocol">Load test protocol</a>
   */
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
        Assert.assertEquals("bad num of passed tests: unittest load protocol failed to find tests?", 3, runner.getPassedTestsCount());
        runner.assertAllTestsPassed();
      }
    });
  }

  /**
   * Ensures that skipped and erroneous tests do not lead to suite ignorance
   */
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
        Assert.assertThat("Bad line highlighted", fileNames, Matchers.everyItem(Matchers.endsWith(fileName)));
      }
    });
  }


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
}
