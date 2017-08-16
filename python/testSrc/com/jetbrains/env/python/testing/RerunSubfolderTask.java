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

import static org.junit.Assert.assertEquals;

/**
 * @author Ilya.Kazakevich
 */
abstract class RerunSubfolderTask<T extends PyScriptTestProcessRunner<?>>
  extends PyProcessWithConsoleTestTask<T> {

  private final int myExpectedFailedTests;

  protected RerunSubfolderTask(final int expectedFailedTests) {
    super("/testRunner/env/nose/subfolder_test", SdkCreationType.EMPTY_SDK);
    myExpectedFailedTests = expectedFailedTests;
  }


  @Override
  protected void checkTestResults(@NotNull T runner,
                                  @NotNull String stdout,
                                  @NotNull String stderr,
                                  @NotNull String all) {
    assertEquals(stderr, myExpectedFailedTests, runner.getFailedTestsCount());
    assertEquals(stderr, myExpectedFailedTests, runner.getAllTestsCount());
  }
}

