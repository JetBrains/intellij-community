package com.jetbrains.env.python.testing;

import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.ut.PyUnitTestTask;
import junit.framework.Assert;

/**
 * @author traff
 */
public class PythonUnitTestingTest extends PyEnvTestCase{
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
