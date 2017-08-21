/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.env.python.testing;

import com.jetbrains.env.PyProcessWithConsoleTestTask;
import com.jetbrains.env.ut.PyScriptTestProcessRunner;
import com.jetbrains.python.tools.sdkTools.SdkCreationType;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

/**
 * Runs test with setup/teardown (script shall be test_test.py) and checks it works
 *
 * @author Ilya.Kazakevich
 */
abstract class SetupTearDownTestTask<T extends PyScriptTestProcessRunner<?>> extends PyProcessWithConsoleTestTask<T> {

  SetupTearDownTestTask() {
    super("testRunner/env/setup_teardown/", SdkCreationType.SDK_PACKAGES_ONLY);
  }

  @Override
  protected final void checkTestResults(@NotNull final T runner,
                                        @NotNull final String stdout,
                                        @NotNull final String stderr,
                                        @NotNull final String all) {

    if (runner.getCurrentRerunStep() == 0) {
      Assert.assertEquals(runner.getFormattedTestTree(), 1, runner.getFailedTestsCount());
      Assert.assertEquals(runner.getFormattedTestTree(), 1, runner.getPassedTestsCount());
      Assert.assertEquals(runner.getFormattedTestTree(), 2, runner.getAllTestsCount());
    }
    else {
      Assert.assertEquals(runner.getFormattedTestTree(), 1, runner.getFailedTestsCount());
      Assert.assertEquals(runner.getFormattedTestTree(), 0, runner.getPassedTestsCount());
      Assert.assertEquals(runner.getFormattedTestTree(), 1, runner.getAllTestsCount());
    }
  }
}
