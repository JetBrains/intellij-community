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

import com.jetbrains.env.PyAbstractTestProcessRunner;
import com.jetbrains.env.PyProcessWithConsoleTestTask;
import com.jetbrains.env.ut.PyScriptTestProcessRunner;
import com.jetbrains.python.tools.sdkTools.SdkCreationType;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

/**
 * Ensures that python target pointing to module works correctly
 * @author Ilya.Kazakevich
 */
abstract class RunModuleAsFileTask<T extends PyAbstractTestProcessRunner<?>> extends PyProcessWithConsoleTestTask<T> {

  protected static final String TARGET = PyScriptTestProcessRunner.TEST_TARGET_PREFIX + "test_some_class";

  RunModuleAsFileTask() {
    super("testRunner/env/runModuleAsFile", SdkCreationType.SDK_PACKAGES_ONLY);
  }


  @Override
  protected final void checkTestResults(@NotNull final T runner,
                                  @NotNull final String stdout,
                                  @NotNull final String stderr,
                                  @NotNull final String all) {
    Assert.assertEquals(stderr, 1, runner.getAllTestsCount());
    Assert.assertEquals(stderr, 1, runner.getPassedTestsCount());
  }
}
