package com.jetbrains.env.python.testing;

import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.ut.PyDocTestTask;

/**
 * User : catherine
 */
public class PythonDocTestingTest extends PyEnvTestCase{
  public void testUTRunner() {
    runPythonTest(new PyDocTestTask("/testRunner/env/doc", "test1.py") {

      @Override
      public void after() {
        assertEquals(3, allTestsCount());
        assertEquals(3, passedTestsCount());
        allTestsPassed();
      }
    });
  }

  public void testClass() {
    runPythonTest(new PyDocTestTask("/testRunner/env/doc", "test1.py::FirstGoodTest") {

      @Override
      public void after() {
        assertEquals(1, allTestsCount());
        assertEquals(1, passedTestsCount());
      }
    });
  }

  public void testMethod() {
    runPythonTest(new PyDocTestTask("/testRunner/env/doc", "test1.py::SecondGoodTest::test_passes") {

      @Override
      public void after() {
        assertEquals(1, allTestsCount());
        assertEquals(1, passedTestsCount());
      }
    });
  }

  public void testFunction() {
    runPythonTest(new PyDocTestTask("/testRunner/env/doc", "test1.py::factorial") {

      @Override
      public void after() {
        assertEquals(1, allTestsCount());
        assertEquals(1, passedTestsCount());
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
