/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.testing;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ModuleRunConfiguration;
import com.intellij.execution.testframework.sm.runner.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Roman.Chernyatchik
 */
public class PythonTRunnerConsoleProperties extends SMTRunnerConsoleProperties {
  public static final String FRAMEWORK_NAME = "PythonUnitTestRunner";

  private final boolean myIsEditable;
  private final SMTestLocator myLocator;

  /**
   * @param editable if user should have ability to print something to test stdin
   */
  public PythonTRunnerConsoleProperties(@NotNull ModuleRunConfiguration config,
                                        @NotNull Executor executor,
                                        boolean editable,
                                        @Nullable SMTestLocator locator) {
    super(config, FRAMEWORK_NAME, executor);
    myIsEditable = editable;
    myLocator = locator;
  }

  @Override
  public boolean isEditable() {
    return myIsEditable;
  }

  @Nullable
  @Override
  public SMTestLocator getTestLocator() {
    return myLocator;
  }

  /**
   * Makes configuration id-based,
   * @see GeneralIdBasedToSMTRunnerEventsConvertor
   */
  final void makeIdTestBased() {
    setIdBasedTestTree(true);
    getProject().getMessageBus().connect(this)
      .subscribe(SMTRunnerEventsListener.TEST_STATUS, new SMTRunnerEventsAdapter() {
        @Override
        public void onTestFailed(@NotNull final SMTestProxy test) {
          SMTestProxy currentTest = test.getParent();
          while (currentTest != null) {
            currentTest.setTestFailed(" ", null, false);
            currentTest = currentTest.getParent();
          }
        }
      });
  }
}