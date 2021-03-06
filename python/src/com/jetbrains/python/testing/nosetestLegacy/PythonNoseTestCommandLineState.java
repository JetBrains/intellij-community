// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.nosetestLegacy;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.target.TargetEnvironmentRequest;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.PythonHelper;
import com.jetbrains.python.run.PythonScriptExecution;
import com.jetbrains.python.run.PythonScripts;
import com.jetbrains.python.testing.PythonTestCommandLineStateBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PythonNoseTestCommandLineState extends PythonTestCommandLineStateBase {
  private final PythonNoseTestRunConfiguration myConfig;

  public PythonNoseTestCommandLineState(PythonNoseTestRunConfiguration runConfiguration, ExecutionEnvironment env) {
    super(runConfiguration, env);
    myConfig = runConfiguration;
  }

  @Override
  protected PythonHelper getRunner() {
    return PythonHelper.NOSE_OLD;
  }

  @Override
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

  @Nullable
  @Override
  protected SMTestLocator getTestLocator() {
    return PythonNoseTestUrlProvider.INSTANCE;
  }

  @Override
  protected void addAfterParameters(GeneralCommandLine cmd) {
    ParamsGroup script_params = cmd.getParametersList().getParamsGroup(GROUP_SCRIPT);
    assert script_params != null;
    if (myConfig.useParam() && !StringUtil.isEmptyOrSpaces(myConfig.getParams())) {
      script_params.addParametersString(myConfig.getParams());
    }
  }

  @Override
  protected void addAfterParameters(@NotNull TargetEnvironmentRequest targetEnvironmentRequest,
                                    @NotNull PythonScriptExecution testScriptExecution) {
    if (myConfig.useParam() && !StringUtil.isEmptyOrSpaces(myConfig.getParams())) {
      PythonScripts.addParametersString(testScriptExecution, myConfig.getParams());
    }
  }
}