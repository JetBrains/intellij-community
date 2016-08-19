/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.testing.nosetest;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.PythonHelper;
import com.jetbrains.python.testing.PythonTestCommandLineStateBase;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * User: catherine
 */
public class PythonNoseTestCommandLineState extends PythonTestCommandLineStateBase {
  private final PythonNoseTestRunConfiguration myConfig;

  public PythonNoseTestCommandLineState(PythonNoseTestRunConfiguration runConfiguration, ExecutionEnvironment env) {
    super(runConfiguration, env);
    myConfig = runConfiguration;
  }

  @Override
  protected PythonHelper getRunner() {
    return PythonHelper.NOSE;
  }

  @NotNull
  protected List<String> getTestSpecs() {
    List<String> specs = new ArrayList<>();

    final String scriptName = FileUtil.toSystemDependentName(myConfig.getScriptName());
    switch (myConfig.getTestType()) {
      case TEST_SCRIPT:
        specs.add(scriptName);
        break;
      case TEST_CLASS:
        specs.add(scriptName + "::" + myConfig.getClassName());
        break;
      case TEST_METHOD:
        specs.add(scriptName + "::" + myConfig.getClassName() + "::" + myConfig.getMethodName());
        break;
      case TEST_FOLDER:
	specs.add(FileUtil.toSystemDependentName(myConfig.getFolderName() + "/"));
        break;
      case TEST_FUNCTION:
        specs.add(scriptName + "::::" + myConfig.getMethodName());
        break;
      default:
        throw new IllegalArgumentException("Unknown test type: " + myConfig.getTestType());
    }
    return specs;
  }

  @Override
  protected void addAfterParameters(GeneralCommandLine cmd)  {
    ParamsGroup script_params = cmd.getParametersList().getParamsGroup(GROUP_SCRIPT);
    assert script_params != null;
    if (myConfig.useParam() && !StringUtil.isEmptyOrSpaces(myConfig.getParams()))
      script_params.addParametersString(myConfig.getParams());

  }
}
