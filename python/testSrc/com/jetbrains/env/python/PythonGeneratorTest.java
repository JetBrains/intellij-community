package com.jetbrains.env.python;

import com.jetbrains.env.PyProcessWithConsoleTestTask;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.ut.PyUnitTestProcessRunner;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.sdkTools.SdkCreationType;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class PythonGeneratorTest extends PyEnvTestCase {
  public void testGenerator() {
    runPythonTest(new PyProcessWithConsoleTestTask<PyUnitTestProcessRunner>(SdkCreationType.EMPTY_SDK) {
      @NotNull
      @Override
      protected PyUnitTestProcessRunner createProcessRunner() throws Exception {
        return new PyUnitTestProcessRunner(PythonHelpersLocator.getPythonCommunityPath() + "/helpers", "test_generator.py", 0);
      }

      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all) {
        runner.assertAllTestsPassed();
      }
    });
  }
}
