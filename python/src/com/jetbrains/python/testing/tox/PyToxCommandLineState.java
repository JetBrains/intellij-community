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
package com.jetbrains.python.testing.tox;

import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.jetbrains.python.HelperPackage;
import com.jetbrains.python.PythonHelper;
import com.jetbrains.python.testing.PythonTestCommandLineStateBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * @author Ilya.Kazakevich
 */
class PyToxCommandLineState extends PythonTestCommandLineStateBase {
  @NotNull
  private final PyToxConfiguration myConfiguration;


  PyToxCommandLineState(@NotNull final PyToxConfiguration configuration,
                        @NotNull final ExecutionEnvironment environment) {
    super(configuration, environment);
    myConfiguration = configuration;
  }

  @Nullable
  @Override
  protected SMTestLocator getTestLocator() {
    return PyToxTestLocator.INSTANCE;
  }

  @Override
  protected HelperPackage getRunner() {
    return PythonHelper.TOX;
  }

  @NotNull
  @Override
  protected List<String> getTestSpecs() {
    return Arrays.asList(myConfiguration.getRunOnlyEnvs());
  }
}