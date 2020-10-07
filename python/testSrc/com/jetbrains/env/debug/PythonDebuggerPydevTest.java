// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.env.debug;

import com.google.common.collect.Sets;
import com.intellij.testFramework.UsefulTestCase;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.PyProcessWithConsoleTestTask;
import com.jetbrains.env.ut.PyTestTestProcessRunner;
import com.jetbrains.env.ut.PyUnitTestProcessRunner;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.tools.sdkTools.SdkCreationType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assume;
import org.junit.Test;

import java.util.Set;

/**
 * The test case for running pure Python tests that are not involve the IDE.
 * It should be used only locally. On TeamCity Python tests should be run separately with `pytest`.
 */
public class PythonDebuggerPydevTest extends PyEnvTestCase {
  @Test
  public void testPydevTests_Debugger() {
    pytests("pydev_tests_python/test_debugger.py", Sets.newHashSet("pytest", "-iron", "untangle"));
  }

  @Test
  public void testPydevMonkey() {
    unittests("pydev_tests_python/test_pydev_monkey.py", null);
  }

  @Test
  public void testBytecodeModification() {
    unittests("pydev_tests_python/test_bytecode_modification.py", Sets.newHashSet("python3.6", "pytest"));
  }

  @Test
  public void testFrameEvalAndTracing() {
    pytests("pydev_tests_python/test_frame_eval_and_tracing.py", Sets.newHashSet("pytest", "-iron", "-python2.7"));
  }

  private void pytests(final String script, @Nullable Set<String> tags) {
    Assume.assumeFalse(UsefulTestCase.IS_UNDER_TEAMCITY);
    runPythonTest(new PyProcessWithConsoleTestTask<PyTestTestProcessRunner>("/helpers/pydev/", SdkCreationType.SDK_PACKAGES_ONLY) {
                    @NotNull
                    @Override
                    protected PyTestTestProcessRunner createProcessRunner() throws Exception {
                      return new PyTestTestProcessRunner(script, 0);
                    }

                    @Override
                    protected void checkTestResults(@NotNull PyTestTestProcessRunner runner,
                                                    @NotNull String stdout,
                                                    @NotNull String stderr,
                                                    @NotNull String all,
                                                    int exitCode) {
                      runner.assertNoFailures();
                    }

                    @NotNull
                    @Override
                    public String getTestDataPath() {
                      return PythonHelpersLocator.getPythonCommunityPath();
                    }

                    @NotNull
                    @Override
                    public Set<String> getTags() {
                      if (tags == null) {
                        return super.getTags();
                      }
                      return tags;
                    }
                  }
    );
  }

  private void unittests(final String script, @Nullable Set<String> tags) {
    unittests(script, tags, false);
  }

  private void unittests(final String script, @Nullable Set<String> tags, boolean isSkipAllowed) {
    Assume.assumeFalse(UsefulTestCase.IS_UNDER_TEAMCITY);
    runPythonTest(new PyProcessWithConsoleTestTask<PyUnitTestProcessRunner>("/helpers/pydev", SdkCreationType.SDK_PACKAGES_ONLY) {

      @NotNull
      @Override
      protected PyUnitTestProcessRunner createProcessRunner() {
        return new PyUnitTestProcessRunner(script, 0);
      }

      @NotNull
      @Override
      public String getTestDataPath() {
        return PythonHelpersLocator.getPythonCommunityPath();
      }

      @Override
      protected void checkTestResults(@NotNull final PyUnitTestProcessRunner runner,
                                      @NotNull final String stdout,
                                      @NotNull final String stderr,
                                      @NotNull final String all, int exitCode) {
        if (isSkipAllowed) {
          runner.assertNoFailures();
        }
        else {
          runner.assertAllTestsPassed();
        }
      }

      @NotNull
      @Override
      public Set<String> getTags() {
        if (tags == null) {
          return super.getTags();
        }
        return tags;
      }
    });
  }
}
