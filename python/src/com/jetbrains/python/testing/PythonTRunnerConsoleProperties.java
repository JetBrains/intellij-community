/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.jetbrains.python.testing.nosetest.PythonNoseTestUrlProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.util.Pair.pair;

/**
 * @author Roman.Chernyatchik
 */
public class PythonTRunnerConsoleProperties extends SMTRunnerConsoleProperties {
  public static final String FRAMEWORK_NAME = "PythonUnitTestRunner";

  private final boolean myIsEditable;

  public PythonTRunnerConsoleProperties(@NotNull ModuleRunConfiguration config, @NotNull Executor executor, boolean editable) {
    super(config, FRAMEWORK_NAME, executor);
    myIsEditable = editable;
  }

  @Override
  public boolean isEditable() {
    return myIsEditable;
  }

  @Nullable
  @Override
  public SMTestLocator getTestLocator() {
    return new SMTestLocator.Composite(
      pair(PythonUnitTestTestIdUrlProvider.PROTOCOL_ID, PythonUnitTestTestIdUrlProvider.INSTANCE),
      pair(PythonNoseTestUrlProvider.PROTOCOL_ID, PythonNoseTestUrlProvider.INSTANCE)
    );
  }
}
