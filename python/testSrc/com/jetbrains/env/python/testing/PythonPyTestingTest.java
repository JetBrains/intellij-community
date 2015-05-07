package com.jetbrains.env.python.testing;

import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.ut.PyTestTestTask;
import org.hamcrest.Matchers;
import org.junit.Assert;

/**
 * User : catherine
 */
public class PythonPyTestingTest extends PyEnvTestCase {
  public void testPytestRunner() {
    runPythonTest(new PyTestTestTask("/testRunner/env/pytest", "test1.py") {

      @Override
      public void after() {
        assertEquals(3, allTestsCount());
        assertEquals(3, passedTestsCount());
        allTestsPassed();
      }
    });
  }

  public void testPytestRunner2() {
    runPythonTest(new PyTestTestTask("/testRunner/env/pytest", "test2.py") {

      @Override
      public void after() {
        assertEquals(9, allTestsCount());
        assertEquals(5, passedTestsCount());
        assertEquals(4, failedTestsCount());
        Assert.assertThat("No test stdout", getMockPrinter(findTestByName("testOne")).getStdOut(), Matchers.startsWith("I am test1"));
        // Ensure test has stdout even it fails
        Assert.assertThat("No stdout for fail", getMockPrinter(findTestByName("testFail")).getStdOut(), Matchers.startsWith("I will fail"));
      }
    });
  }
}
