package com.jetbrains.env.python.testing;

import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.env.PyEnvTaskRunner;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.ut.PyTestTestTask;
import com.jetbrains.env.ut.PyUnitTestTask;
import org.hamcrest.Matchers;
import org.junit.Assert;

import java.util.List;

/**
 * @author traff
 */
public class PythonUnitTestingTest extends PyEnvTestCase {
  public void testUTRunner() {
    runPythonTest(new PyUnitTestTask("/testRunner/env/unit", "test1.py") {

      @Override
      public void after() {
        Assert.assertEquals(2, allTestsCount());
        Assert.assertEquals(2, passedTestsCount());
        allTestsPassed();
      }
    });
  }

  /**
   * Ensures that skipped and erroneous tests do not lead to suite ignorance
   */
  public void testUTSkippedAndIgnored() {
    runPythonTest(new PyUnitTestTask("/testRunner/env/unit", "test_with_skips_and_errors.py") {

      @Override
      public void runTestOn(final String sdkHome) throws Exception {
        if (!PyEnvTaskRunner.isJython(sdkHome)) {
          // Temporary Crunch to disable this test on Jython
          super.runTestOn(sdkHome);
        }
      }

      @Override
      public void after() {
        assertEquals(4, allTestsCount());
        assertEquals(2, passedTestsCount());
        assertEquals(2, failedTestsCount());
        Assert.assertFalse("Suite is not finished", myTestProxy.isInterrupted());
      }
    });
  }

  public void testUTRunner2() {
    runPythonTest(new PyUnitTestTask("/testRunner/env/unit", "test2.py") {

      @Override
      public void after() {
        assertEquals(3, allTestsCount());
        assertEquals(1, passedTestsCount());
        assertEquals(2, failedTestsCount());
      }
    });
  }

  /**
   * Ensures pattern is supported
   */
  public void testUTRunnerByPattern() {
    runPythonTest(new PyUnitTestTask("/testRunner/env/unit", "_args_separator_*pattern.py") {

      @Override
      public void after() {
        assertEquals(4, allTestsCount());
        assertEquals(2, passedTestsCount());
        assertEquals(2, failedTestsCount());
      }
    });
  }

  public void testClass() {
    runPythonTest(new PyUnitTestTask("/testRunner/env/unit", "test_file.py::GoodTest") {

      @Override
      public void after() {
        assertEquals(1, allTestsCount());
        assertEquals(1, passedTestsCount());
      }
    });
  }

  public void testMethod() {
    runPythonTest(new PyUnitTestTask("/testRunner/env/unit", "test_file.py::GoodTest::test_passes") {

      @Override
      public void after() {
        assertEquals(1, allTestsCount());
        assertEquals(1, passedTestsCount());
      }
    });
  }

  public void testFolder() {
    runPythonTest(new PyUnitTestTask("/testRunner/env/unit", "test_folder/") {

      @Override
      public void after() {
        assertEquals(5, allTestsCount());
        assertEquals(3, passedTestsCount());
      }
    });
  }

  /**
   * Ensures file references are highlighted for python traceback
   */
  public void testUnitTestFileReferences() {
    final String fileName = "reference_tests.py";
    runPythonTest(new PyUnitTestTask("/testRunner/env/unit", fileName) {

      @Override
      public void after() {
        final List<String> fileNames = getHighlightedStrings().second;
        Assert.assertThat(String.format("Wrong number of highlighted entries(%s)", StringUtil.join(fileNames, ",")),
                          fileNames, Matchers.hasSize(3));
        // UnitTest highlights file name
        Assert.assertThat("Bad line highlighted", fileNames, Matchers.everyItem(Matchers.endsWith(fileName)));
      }
    });
  }

  /**
   * Ensures file references are highlighted for pytest traceback
   */
  public void testPyTestFileReferences() {
    final String fileName = "reference_tests.py";
    runPythonTest(new PyTestTestTask("/testRunner/env/unit", fileName) {

      @Override
      public void after() {
        final List<String> fileNames = getHighlightedStrings().second;
        Assert.assertThat("No lines highlighted", fileNames, Matchers.not(Matchers.empty()));
        // PyTest highlights file:line_number
        Assert.assertTrue("Assert fail not marked", fileNames.contains("reference_tests.py:7"));
        Assert.assertTrue("Failed test not marked", fileNames.contains("reference_tests.py:12"));
      }
    });
  }


  public void testDependent() {
    runPythonTest(new PyUnitTestTask("/testRunner/env/unit", "dependentTests/test_my_class.py") {

      @Override
      public void after() {
        assertEquals(1, allTestsCount());
        assertEquals(1, passedTestsCount());
      }
    });
  }
}
