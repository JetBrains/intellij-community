package com.jetbrains.env.python.testing;

import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.ut.PyTestTestTask;

/**
 * User : catherine
 */
public class PythonPyTestingTest extends PyEnvTestCase{
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
        assertEquals(8, allTestsCount());
        assertEquals(5, passedTestsCount());
        assertEquals(3, failedTestsCount());
      }
    });
  }
}
