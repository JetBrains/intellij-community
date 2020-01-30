// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.pytestLegacy;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.target.TargetEnvironmentRequest;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.HelperPackage;
import com.jetbrains.python.PythonHelper;
import com.jetbrains.python.run.PythonScriptExecution;
import com.jetbrains.python.run.PythonScripts;
import com.jetbrains.python.testing.PythonTestCommandLineStateBase;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class PyTestCommandLineState extends PythonTestCommandLineStateBase {
  private final PyTestRunConfiguration myConfiguration;

  public PyTestCommandLineState(PyTestRunConfiguration configuration, ExecutionEnvironment env) {
    super(configuration, env);
    myConfiguration = configuration;
  }

  @Override
  protected void addBeforeParameters(GeneralCommandLine cmd) {
    ParamsGroup script_params = cmd.getParametersList().getParamsGroup(GROUP_SCRIPT);
    assert script_params != null;
    script_params.addParameters("-p", "pytest_teamcity");
  }

  @Override
  protected HelperPackage getRunner() {
    return PythonHelper.PYTEST_OLD;
  }

  @NotNull
  @Override
  protected List<String> getTestSpecs() {
    List<String> specs = new ArrayList<>();
    specs.add(myConfiguration.getTestToRun());
    return specs;
  }

  @Override
  protected void addAfterParameters(GeneralCommandLine cmd) {
    ParamsGroup script_params = cmd.getParametersList().getParamsGroup(GROUP_SCRIPT);
    assert script_params != null;
    String params = myConfiguration.getParams();
    if (!StringUtil.isEmptyOrSpaces(params)) {
      script_params.addParametersString(params);
    }

    String keywords = myConfiguration.getKeywords();
    if (!StringUtil.isEmptyOrSpaces(keywords)) {
      script_params.addParameter("-k " + keywords);
    }
  }

  @Override
  protected void addBeforeParameters(@NotNull PythonScriptExecution testScriptExecution) {
    testScriptExecution.addParameters("-p", "pytest_teamcity");
  }

  @Override
  protected void addAfterParameters(@NotNull TargetEnvironmentRequest targetEnvironmentRequest,
                                    @NotNull PythonScriptExecution testScriptExecution) {
    String params = myConfiguration.getParams();
    if (!StringUtil.isEmptyOrSpaces(params)) {
      PythonScripts.addParametersString(testScriptExecution, params);
    }

    String keywords = myConfiguration.getKeywords();
    if (!StringUtil.isEmptyOrSpaces(keywords)) {
      testScriptExecution.addParameter("-k " + keywords);
    }
  }

  @Override
  @NotNull
  protected ConsoleView createAndAttachConsole(Project project, ProcessHandler processHandler, Executor executor)
    throws ExecutionException {
    final ConsoleView consoleView = super.createAndAttachConsole(project, processHandler, executor);
    addTracebackFilter(project, consoleView, processHandler);
    return consoleView;
  }
}
