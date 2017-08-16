package com.jetbrains.env.python;

import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.PyProcessWithConsoleTestTask;
import com.jetbrains.env.ut.PyUnitTestProcessRunner;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.sdkTools.SdkCreationType;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

/**
 * @author traff
 */
public class PythonGeneratorTest extends PyEnvTestCase {
  @Test
  public void testGenerator() {
    runPythonTest(new PyProcessWithConsoleTestTask<PyUnitTestProcessRunner>("/helpers", SdkCreationType.EMPTY_SDK) {
      @NotNull
      @Override
      protected PyUnitTestProcessRunner createProcessRunner() {
        return new PyUnitTestProcessRunner("test_generator.py", 0);
      }

      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        runner.assertAllTestsPassed();
      }

      @NotNull
      @Override
      protected String getTestDataPath() {
        return PythonHelpersLocator.getPythonCommunityPath();
      }
    });
  }
}
