package com.jetbrains.env.python;

import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.ut.PyUnitTestTask;
import com.jetbrains.python.PythonHelpersLocator;

/**
 * @author traff
 */
public class PythonGeneratorTest extends PyEnvTestCase{
  public void testGenerator() {
    runPythonTest(new PyUnitTestTask("", "test_generator.py") {
      @Override
      protected String getTestDataPath() {
        return PythonHelpersLocator.getPythonCommunityPath() + "/helpers";
      }

      @Override
      public void after() {
        allTestsPassed();
      }
    });
  }
}
