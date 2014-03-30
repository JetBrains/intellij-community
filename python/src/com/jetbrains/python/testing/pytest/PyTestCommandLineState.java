/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.testing.pytest;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.testing.PythonTestCommandLineStateBase;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class PyTestCommandLineState extends PythonTestCommandLineStateBase {
  private final PyTestRunConfiguration myConfiguration;
  private static final String PYTESTRUNNER_PY = "pycharm/pytestrunner.py";

  public PyTestCommandLineState(PyTestRunConfiguration configuration, ExecutionEnvironment env) {
    super(configuration, env);
    myConfiguration = configuration;
  }

  @Override
  protected void addBeforeParameters(GeneralCommandLine cmd) throws ExecutionException {
    ParamsGroup script_params = cmd.getParametersList().getParamsGroup(GROUP_SCRIPT);
    assert script_params != null;
    script_params.addParameters("-p", "pytest_teamcity");
  }

  @Override
  protected String getRunner() {
    return PYTESTRUNNER_PY;
  }

  @Override
  protected List<String> getTestSpecs() {
    List<String> specs = new ArrayList<String>();
    specs.add(myConfiguration.getTestToRun());
    return specs;
  }

  @Override
  protected void addAfterParameters(GeneralCommandLine cmd) throws ExecutionException {
    ParamsGroup script_params = cmd.getParametersList().getParamsGroup(GROUP_SCRIPT);
    assert script_params != null;
    String params = myConfiguration.getParams();
    if (!StringUtil.isEmptyOrSpaces(params)) {
      for (String p : StringUtil.splitHonorQuotes(params, ' '))
        script_params.addParameter(p);
    }

    String keywords = myConfiguration.getKeywords();
    if (!StringUtil.isEmptyOrSpaces(keywords)) {
      script_params.addParameter("-k " + keywords);
    }

  }

  @NotNull
  protected ConsoleView createAndAttachConsole(Project project, ProcessHandler processHandler, Executor executor)
    throws ExecutionException {
    final ConsoleView consoleView = super.createAndAttachConsole(project, processHandler, executor);
    consoleView.addMessageFilter(new PyTestTracebackFilter(project, myConfiguration.getWorkingDirectory()));
    return consoleView;
  }
}
