package com.jetbrains.env.community.python.testing;

import com.jetbrains.env.community.PyEnvTestCase;
import com.jetbrains.env.community.ut.PyNoseTestTask;

/**
 * User : catherine
 */
public class PythonNoseTestingTest extends PyEnvTestCase{
  public void testNoseRunner() {
    runPythonTest(new PyNoseTestTask("/testRunner/env/nose", "test1.py") {

      @Override
      public void after() {
        assertEquals(3, allTestsCount());
        assertEquals(3, passedTestsCount());
        allTestsPassed();
      }
    });
  }

  public void testNoseRunner2() {
    runPythonTest(new PyNoseTestTask("/testRunner/env/nose", "test2.py") {

      @Override
      public void after() {
        assertEquals(8, allTestsCount());
        assertEquals(5, passedTestsCount());
        assertEquals(3, failedTestsCount());
      }
    });
  }
}
