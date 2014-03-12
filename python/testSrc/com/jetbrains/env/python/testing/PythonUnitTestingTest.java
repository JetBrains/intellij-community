package com.jetbrains.env.python.testing;

import com.jetbrains.env.python.debug.PyEnvTestCase;
import com.jetbrains.env.ut.PyUnitTestTask;
import junit.framework.Assert;

/**
 * @author traff
 */
public class PythonUnitTestingTest extends PyEnvTestCase{
  public void testUTRunner() {
    runPythonTest(new PyUnitTestTask("/testRunner/env", "test1.py") {

      @Override
      public void after() {
        Assert.assertEquals(2, allTestsCount());
        Assert.assertEquals(2, passedTestsCount());
        allTestsPassed();
      }
    });
  }

  public void testUTRunner2() {
    runPythonTest(new PyUnitTestTask("/testRunner/env", "test2.py") {

      @Override
      public void after() {
        assertEquals(3, allTestsCount());
        assertEquals(1, passedTestsCount());
        assertEquals(2, failedTestsCount());
      }
    });
  }
}
