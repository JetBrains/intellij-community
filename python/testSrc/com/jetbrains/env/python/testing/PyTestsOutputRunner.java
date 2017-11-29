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
import com.intellij.execution.testframework.sm.runner.ui.MockPrinter;
import com.jetbrains.env.ut.PyScriptTestProcessRunner;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

/**
 * Checks test output is correct
 */
abstract class PyTestsOutputRunner<T extends PyScriptTestProcessRunner<?>> extends PyTestsFunctionBasedRunner<T> {

  PyTestsOutputRunner(@NotNull final String... functionsToCheck) {
    super(functionsToCheck);
  }

  @Override
  protected void checkMethod(@NotNull final AbstractTestProxy method, @NotNull final String functionName) {
    if (functionName.endsWith("test_metheggs")) {
      Assert.assertThat("Method output is broken",
                        MockPrinter.fillPrinter(method).getStdOut().trim(), Matchers.containsString("I am method"));
    }
    else if (functionName.endsWith("test_funeggs")) {
      Assert.assertThat("Function output is broken",
                        MockPrinter.fillPrinter(method).getStdOut().trim(), Matchers.containsString("I am function"));
    }
    else if (functionName.endsWith("test_first") || functionName.endsWith("test_second")) {
      // No output expected
    }
    else {
      throw new AssertionError("Unknown function " + functionName);
    }
  }
}
