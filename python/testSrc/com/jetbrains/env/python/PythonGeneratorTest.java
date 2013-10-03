package com.jetbrains.env.python;

import com.intellij.openapi.application.PathManager;
import com.jetbrains.env.python.debug.PyEnvTestCase;
import com.jetbrains.env.ut.PyUnitTestTask;

/**
 * @author traff
 */
public class PythonGeneratorTest extends PyEnvTestCase{
  public void testGenerator() {
    runPythonTest(new PyUnitTestTask("", "test_generator.py") {
      @Override
      protected String getTestDataPath() {
        return PathManager.getHomePath() + "/python/community/helpers";
      }

      @Override
      public void after() {
        allTestsPassed();
      }
    });
  }
}
