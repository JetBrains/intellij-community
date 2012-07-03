package com.jetbrains.env.python;

import com.jetbrains.env.python.debug.PyEnvTestCase;
import com.jetbrains.env.ut.PyDocTestTask;
import junit.framework.Assert;

/**
 * User : catherine
 */
public class PythonDocTestingTest extends PyEnvTestCase{
  public void testUTRunner() {
    runPythonTest(new PyDocTestTask("/testRunner/env/doc", "test1.py") {

      @Override
      public void after() {
        Assert.assertEquals(3, allTestsCount());
        Assert.assertEquals(3, passedTestsCount());
        allTestsPassed();
      }
    });
  }

  public void testUTRunner2() {
    runPythonTest(new PyDocTestTask("/testRunner/env/doc", "test2.py") {

      @Override
      public void after() {
        assertEquals(3, allTestsCount());
        assertEquals(1, passedTestsCount());
        assertEquals(2, failedTestsCount());
      }
    });
  }
}
