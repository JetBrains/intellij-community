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
package com.jetbrains.python.testing.tox;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.HelperPackage;
import com.jetbrains.python.PythonHelper;
import com.jetbrains.python.testing.PythonTestCommandLineStateBase;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
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

  @Override
  protected HelperPackage getRunner() {
    return PythonHelper.TOX;
  }


  @Override
  public GeneralCommandLine generateCommandLine() {
    final GeneralCommandLine line = super.generateCommandLine();
    final ParamsGroup group = line.getParametersList().getParamsGroup(GROUP_SCRIPT);
    assert group != null : "No group " + GROUP_SCRIPT;
    final String[] envs = myConfiguration.getRunOnlyEnvs();
    if (envs.length > 0) {
      group.addParameter(String.format("-e %s", StringUtil.join(envs, ",")));
    }
    group.addParameters(myConfiguration.getArguments());
    return line;
  }

  @NotNull
  @Override
  protected List<String> getTestSpecs() {
    return Collections.emptyList();
  }
}
