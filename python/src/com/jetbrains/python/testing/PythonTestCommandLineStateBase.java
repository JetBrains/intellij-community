// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing;

import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.target.TargetEnvironment;
import com.intellij.execution.target.TargetEnvironmentRequest;
import com.intellij.execution.target.value.TargetEnvironmentFunctions;
import com.intellij.execution.testframework.autotest.ToggleAutoTestAction;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView;
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.HelperPackage;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.console.PythonDebugLanguageConsoleView;
import com.jetbrains.python.run.*;
import com.jetbrains.python.run.target.HelpersAwareTargetEnvironmentRequest;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;


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
        new PythonDebugLanguageConsoleView(project, PythonSdkUtil.findSdkByPath(myConfiguration.getInterpreterPath()),
                                           testsOutputConsoleView, true);
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

  @NotNull
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

  @Override
  protected @NotNull PythonExecution buildPythonExecution(@NotNull HelpersAwareTargetEnvironmentRequest helpersAwareRequest) {
    TargetEnvironmentRequest targetEnvironmentRequest = helpersAwareRequest.getTargetEnvironmentRequest();
    PythonScriptExecution testScriptExecution = PythonScripts.prepareHelperScriptExecution(getRunner(), helpersAwareRequest);
    addBeforeParameters(testScriptExecution);
    addTestSpecsAsParameters(testScriptExecution, getTestSpecs(targetEnvironmentRequest));
    addAfterParameters(targetEnvironmentRequest, testScriptExecution);
    return testScriptExecution;
  }

  @Override
  protected @Nullable Function<TargetEnvironment, String> getPythonExecutionWorkingDir(@NotNull TargetEnvironmentRequest request) {
    Function<TargetEnvironment, String> workingDir = super.getPythonExecutionWorkingDir(request);
    if (workingDir != null) {
      return workingDir;
    }
    return TargetEnvironmentFunctions.getTargetEnvironmentValueForLocalPath(request, myConfiguration.getWorkingDirectorySafe());
  }

  protected void setWorkingDirectory(@NotNull final GeneralCommandLine cmd) {
    String workingDirectory = myConfiguration.getWorkingDirectory();
    if (StringUtil.isEmptyOrSpaces(workingDirectory)) {
      workingDirectory = myConfiguration.getWorkingDirectorySafe();
    }
    cmd.withWorkDirectory(workingDirectory);
  }

  @Override
  public ExecutionResult execute(Executor executor, PythonProcessStarter processStarter, CommandLinePatcher... patchers)
    throws ExecutionException {
    final ProcessHandler processHandler = startProcess(processStarter, patchers);
    ConsoleView console = invokeAndWait(() -> createAndAttachConsole(myConfiguration.getProject(), processHandler, executor));

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

  @Override
  public @NotNull ExecutionResult execute(Executor executor, @NotNull PythonScriptTargetedCommandLineBuilder converter)
    throws ExecutionException {
    ProcessHandler processHandler = startProcess(converter);
    ConsoleView console = invokeAndWait(() -> createAndAttachConsole(myConfiguration.getProject(), processHandler, executor));

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

  protected static <T, E extends Throwable> @NotNull T invokeAndWait(ThrowableComputable<@NotNull T, E> computable) {
    AsyncPromise<T> promise = new AsyncPromise<>();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      try {
        promise.setResult(computable.compute());
      }
      catch (Throwable error) {
        promise.setError(error);
      }
    });
    return Objects.requireNonNull(promise.get(), "The execution was cancelled");
  }

  /**
   * To be deprecated.
   * <p>
   * The part of the legacy implementation based on {@link GeneralCommandLine}.
   */
  protected void addBeforeParameters(GeneralCommandLine cmd) { }

  /**
   * To be deprecated.
   * <p>
   * The part of the legacy implementation based on {@link GeneralCommandLine}.
   */
  protected void addAfterParameters(GeneralCommandLine cmd) { }

  /**
   * To be deprecated.
   * <p>
   * The part of the legacy implementation based on {@link GeneralCommandLine}.
   */
  protected void addTestRunnerParameters(GeneralCommandLine cmd) {
    ParamsGroup scriptParams = cmd.getParametersList().getParamsGroup(GROUP_SCRIPT);
    assert scriptParams != null;
    getRunner().addToGroup(scriptParams, cmd);
    addBeforeParameters(cmd);
    myConfiguration.addTestSpecsAsParameters(scriptParams, getTestSpecs());
    addAfterParameters(cmd);
  }

  protected void addBeforeParameters(@NotNull PythonScriptExecution testScriptExecution) { }

  /**
   * Adds test specs (like method, class, script, etc) to list of runner parameters.
   * <p>
   * Works together with {@link #getTestSpecs(TargetEnvironmentRequest)}.
   */
  protected void addTestSpecsAsParameters(@NotNull PythonScriptExecution testScriptExecution,
                                          @NotNull List<Function<TargetEnvironment, String>> testSpecs) {
    // By default, we simply add them as arguments
    testSpecs.forEach(parameter -> testScriptExecution.addParameter(parameter));
  }

  protected void addAfterParameters(@NotNull TargetEnvironmentRequest targetEnvironmentRequest,
                                    @NotNull PythonScriptExecution testScriptExecution) { }

  @Override
  public void customizeEnvironmentVars(Map<String, String> envs, boolean passParentEnvs) {
    super.customizeEnvironmentVars(envs, passParentEnvs);
    envs.put("PYCHARM_HELPERS_DIR", PythonHelpersLocator.getHelperPath("pycharm"));
  }

  @Override
  protected void customizePythonExecutionEnvironmentVars(@NotNull HelpersAwareTargetEnvironmentRequest helpersAwareTargetRequest,
                                                         @NotNull Map<String, Function<TargetEnvironment, String>> envs,
                                                         boolean passParentEnvs) {
    super.customizePythonExecutionEnvironmentVars(helpersAwareTargetRequest, envs, passParentEnvs);
    Function<TargetEnvironment, String> helpersTargetPath = helpersAwareTargetRequest.preparePyCharmHelpers();
    Function<TargetEnvironment, String> targetPycharmHelpersPath =
      TargetEnvironmentFunctions.getRelativeTargetPath(helpersTargetPath, "pycharm");
    envs.put("PYCHARM_HELPERS_DIR", targetPycharmHelpersPath);
  }

  protected abstract HelperPackage getRunner();

  /**
   * <i>To be deprecated. The part of the legacy implementation based on {@link GeneralCommandLine}.</i>
   */
  @NotNull
  protected abstract List<String> getTestSpecs();

  /**
   * Returns the list of specifications for tests to be executed.
   * <p>
   * Works together with {@link #addTestSpecsAsParameters(PythonScriptExecution, List)}.
   */
  protected @NotNull List<Function<TargetEnvironment, String>> getTestSpecs(@NotNull TargetEnvironmentRequest request) {
    return List.of();
  }
}
