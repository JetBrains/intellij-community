// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.python;

import com.intellij.python.community.helpersLocator.PythonHelpersLocator;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.PyProcessWithConsoleTestTask;
import com.jetbrains.env.ut.PyUnitTestProcessRunner;
import com.jetbrains.python.tools.sdkTools.SdkCreationType;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

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
                                      @NotNull final String all, int exitCode) {
        runner.assertAllTestsPassed();
      }

      @NotNull
      @Override
      protected String getTestDataPath() {
        return PythonHelpersLocator.getPythonCommunityPath().toString();
      }
    });
  }
}
