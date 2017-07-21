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

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.openapi.application.ReadAction;
import com.intellij.util.ThrowableRunnable;
import com.jetbrains.env.PyProcessWithConsoleTestTask;
import com.jetbrains.env.ut.PyScriptTestProcessRunner;
import com.jetbrains.python.sdkTools.SdkCreationType;
import org.jetbrains.annotations.NotNull;

/**
 * Checks each method by name
 */
abstract class PyTestsFunctionBasedRunner<T extends PyScriptTestProcessRunner<?>> extends PyProcessWithConsoleTestTask<T> {
  @NotNull
  protected final String[] myFunctionsToCheck;

  protected PyTestsFunctionBasedRunner(@NotNull final String... functionsToCheck) {
    super("/testRunner/env/testsInFolder", SdkCreationType.EMPTY_SDK);
    assert functionsToCheck.length > 0 : "Provide functions";
    myFunctionsToCheck = functionsToCheck.clone();
  }

  @Override
  protected final void checkTestResults(@NotNull final T runner,
                                        @NotNull final String stdout,
                                        @NotNull final String stderr,
                                        @NotNull final String all) {
    for (final String functionName : myFunctionsToCheck) {
      ReadAction.run((ThrowableRunnable<AssertionError>)() -> {
        final AbstractTestProxy method = runner.findTestByName(functionName);
        checkMethod(method, functionName);
      });
    }
  }

  /**
   * Called for each method
   */
  protected abstract void checkMethod(@NotNull final AbstractTestProxy method, @NotNull final String functionName);
}
