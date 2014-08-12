package com.jetbrains.env.community.python;

import com.jetbrains.env.community.PyEnvTestCase;
import com.jetbrains.env.community.ut.PyUnitTestTask;
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
