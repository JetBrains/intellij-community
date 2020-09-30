// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.unittestLegacy;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.target.TargetEnvironmentRequest;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonHelper;
import com.jetbrains.python.run.PythonScriptExecution;
import com.jetbrains.python.testing.AbstractPythonLegacyTestRunConfiguration;
import com.jetbrains.python.testing.PythonTestCommandLineStateBase;
import com.jetbrains.python.testing.PythonUnitTestTestIdUrlProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Leonid Shalupov
 */
public class PythonUnitTestCommandLineState extends PythonTestCommandLineStateBase {
  private final PythonUnitTestRunConfiguration myConfig;

  public PythonUnitTestCommandLineState(PythonUnitTestRunConfiguration runConfiguration, ExecutionEnvironment env) {
    super(runConfiguration, env);
    myConfig = runConfiguration;
  }

  @Override
  protected PythonHelper getRunner() {
    if (myConfig.getTestType() == AbstractPythonLegacyTestRunConfiguration.TestType.TEST_SCRIPT &&
        myConfig.getScriptName().endsWith(PyNames.SETUP_DOT_PY))
      return PythonHelper.SETUPPY;
    return PythonHelper.UT_OLD;
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
        final String folderName = FileUtil.toSystemDependentName(myConfig.getFolderName() + "/");
        if (!StringUtil.isEmpty(myConfig.getPattern()) && myConfig.usePattern()) {
          // ";" can't be used with bash, so we use "_args_separator_"
          specs.add(folderName + "_args_separator_" + myConfig.getPattern());
        }
        else {
          specs.add(folderName);
        }
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
    return PythonUnitTestTestIdUrlProvider.INSTANCE;
  }

  @Override
  protected void addAfterParameters(GeneralCommandLine cmd) {
    ParamsGroup script_params = cmd.getParametersList().getParamsGroup(GROUP_SCRIPT);
    assert script_params != null;
    if (myConfig.useParam() && !StringUtil.isEmptyOrSpaces(myConfig.getParams()))
      script_params.addParameter(myConfig.getParams());

    if (myConfig.getTestType() != AbstractPythonLegacyTestRunConfiguration.TestType.TEST_SCRIPT ||
        !myConfig.getScriptName().endsWith(PyNames.SETUP_DOT_PY))
      script_params.addParameter(String.valueOf(myConfig.isPureUnittest()));
  }

  @Override
  protected void addAfterParameters(@NotNull TargetEnvironmentRequest targetEnvironmentRequest,
                                    @NotNull PythonScriptExecution testScriptExecution) {
    if (myConfig.useParam() && !StringUtil.isEmptyOrSpaces(myConfig.getParams())) {
      testScriptExecution.addParameter(myConfig.getParams());
    }

    if (myConfig.getTestType() != AbstractPythonLegacyTestRunConfiguration.TestType.TEST_SCRIPT ||
        !myConfig.getScriptName().endsWith(PyNames.SETUP_DOT_PY)) {
      testScriptExecution.addParameter(String.valueOf(myConfig.isPureUnittest()));
    }
  }
}