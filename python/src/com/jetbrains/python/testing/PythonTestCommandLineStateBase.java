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

import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.autotest.ToggleAutoTestAction;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView;
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.HelperPackage;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.console.PythonDebugLanguageConsoleView;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.run.CommandLinePatcher;
import com.jetbrains.python.run.PythonCommandLineState;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public abstract class PythonTestCommandLineStateBase<T extends AbstractPythonRunConfiguration<?>> extends PythonCommandLineState {
  protected final T myConfiguration;

  public T getConfiguration() {
    return myConfiguration;
  }

  public PythonTestCommandLineStateBase(T configuration, ExecutionEnvironment env) {
    super(configuration, env);
    myConfiguration = configuration;
    setRunWithPty(false);
  }

  @Override
  @NotNull
  protected ConsoleView createAndAttachConsole(Project project, ProcessHandler processHandler, Executor executor)
    throws ExecutionException {

    final PythonTRunnerConsoleProperties consoleProperties = createConsoleProperties(executor);

    if (isDebug()) {
      final ConsoleView testsOutputConsoleView = SMTestRunnerConnectionUtil.createConsole(PythonTRunnerConsoleProperties.FRAMEWORK_NAME,
                                                                                          consoleProperties);
      final ConsoleView consoleView =
        new PythonDebugLanguageConsoleView(project, PythonSdkType.findSdkByPath(myConfiguration.getInterpreterPath()),
                                           testsOutputConsoleView);
      consoleView.attachToProcess(processHandler);
      addTracebackFilter(project, consoleView, processHandler);
      return consoleView;
    }
    final ConsoleView consoleView = SMTestRunnerConnectionUtil.createAndAttachConsole(PythonTRunnerConsoleProperties.FRAMEWORK_NAME,
                                                                                      processHandler,
                                                                                      consoleProperties);
    addTracebackFilter(project, consoleView, processHandler);
    return consoleView;
  }

  protected PythonTRunnerConsoleProperties createConsoleProperties(Executor executor) {
    final PythonTRunnerConsoleProperties properties = new PythonTRunnerConsoleProperties(myConfiguration, executor, true, getTestLocator());
    if (myConfiguration.isIdTestBased()) {
      properties.makeIdTestBased();
    }
    return properties;
  }

  @Nullable
  protected SMTestLocator getTestLocator() {
    return null;  // by default, the IDE will use a "file://" protocol locator
  }

  @Override
  public GeneralCommandLine generateCommandLine() {
    GeneralCommandLine cmd = super.generateCommandLine();

    setWorkingDirectory(cmd);

    ParamsGroup exe_options = cmd.getParametersList().getParamsGroup(GROUP_EXE_OPTIONS);
    assert exe_options != null;
    exe_options.addParametersString(myConfiguration.getInterpreterOptions());
    addTestRunnerParameters(cmd);

    return cmd;
  }

  protected void setWorkingDirectory(@NotNull final GeneralCommandLine cmd) {
    String workingDirectory = myConfiguration.getWorkingDirectory();
    if (StringUtil.isEmptyOrSpaces(workingDirectory)) {
      workingDirectory = myConfiguration.getWorkingDirectorySafe();
    }
    cmd.withWorkDirectory(workingDirectory);
  }

  @Override
  public ExecutionResult execute(Executor executor, PythonProcessStarter processStarter, CommandLinePatcher... patchers) throws ExecutionException {
    final ProcessHandler processHandler = startProcess(processStarter, patchers);
    final ConsoleView console = createAndAttachConsole(myConfiguration.getProject(), processHandler, executor);

    DefaultExecutionResult executionResult =
      new DefaultExecutionResult(console, processHandler, createActions(console, processHandler));

    PyRerunFailedTestsAction rerunFailedTestsAction = new PyRerunFailedTestsAction(console);
    if (console instanceof SMTRunnerConsoleView) {
      rerunFailedTestsAction.init(((BaseTestsOutputConsoleView)console).getProperties());
      rerunFailedTestsAction.setModelProvider(() -> ((SMTRunnerConsoleView)console).getResultsViewer());
    }

    executionResult.setRestartActions(rerunFailedTestsAction, new ToggleAutoTestAction());
    return executionResult;
  }

  protected void addBeforeParameters(GeneralCommandLine cmd) {}

  protected void addAfterParameters(GeneralCommandLine cmd) {}

  protected void addTestRunnerParameters(GeneralCommandLine cmd) {
    ParamsGroup scriptParams = cmd.getParametersList().getParamsGroup(GROUP_SCRIPT);
    assert scriptParams != null;
    getRunner().addToGroup(scriptParams, cmd);
    addBeforeParameters(cmd);
    myConfiguration.addTestSpecsAsParameters(scriptParams, getTestSpecs());
    addAfterParameters(cmd);
  }

  @Override
  public void customizeEnvironmentVars(Map<String, String> envs, boolean passParentEnvs) {
    super.customizeEnvironmentVars(envs, passParentEnvs);
    envs.put("PYCHARM_HELPERS_DIR", PythonHelpersLocator.getHelperPath("pycharm"));
  }

  protected abstract HelperPackage getRunner();

  @NotNull
  protected abstract List<String> getTestSpecs();
}