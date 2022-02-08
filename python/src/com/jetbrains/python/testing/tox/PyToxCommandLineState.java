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
import com.intellij.execution.target.TargetEnvironment;
import com.intellij.execution.target.TargetEnvironmentRequest;
import com.intellij.execution.target.value.TargetEnvironmentFunctions;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.HelperPackage;
import com.jetbrains.python.PythonHelper;
import com.jetbrains.python.run.PythonScriptExecution;
import com.jetbrains.python.testing.PythonTestCommandLineStateBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static com.intellij.execution.target.value.TargetEnvironmentFunctions.constant;
import static com.intellij.execution.target.value.TargetEnvironmentFunctions.joinToStringFunction;

/**
 * @author Ilya.Kazakevich
 */
class PyToxCommandLineState extends PythonTestCommandLineStateBase<PyToxConfiguration> {
  PyToxCommandLineState(@NotNull final PyToxConfiguration configuration,
                        @NotNull final ExecutionEnvironment environment) {
    super(configuration, environment);
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

  @Override
  protected void addTestSpecsAsParameters(@NotNull PythonScriptExecution testScriptExecution,
                                          @NotNull List<Function<TargetEnvironment, String>> testSpecs) {
    if (!testSpecs.isEmpty()) {
      Function<TargetEnvironment, String> envOptionValue = joinToStringFunction(testSpecs, ",");
      testScriptExecution.addParameter(joinToStringFunction(List.of(constant("-e"), envOptionValue), " "));
    }
    for (String argument : myConfiguration.getArguments()) {
      testScriptExecution.addParameter(argument);
    }
  }

  /**
   * <i>To be deprecated. The part of the legacy implementation based on
   * {@link com.intellij.execution.configurations.GeneralCommandLine}.</i>
   */
  @NotNull
  @Override
  protected List<String> getTestSpecs() {
    return Arrays.asList(myConfiguration.getRunOnlyEnvs());
  }

  @Override
  protected @NotNull List<Function<TargetEnvironment, String>> getTestSpecs(@NotNull TargetEnvironmentRequest request) {
    return ContainerUtil.map(myConfiguration.getRunOnlyEnvs(), TargetEnvironmentFunctions::constant);
  }
}