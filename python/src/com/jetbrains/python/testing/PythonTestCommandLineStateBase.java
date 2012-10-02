package com.jetbrains.python.testing;

import com.google.common.collect.Lists;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.execution.testframework.autotest.ToggleAutoTestAction;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView;
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.console.PythonDebugLanguageConsoleView;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.run.CommandLinePatcher;
import com.jetbrains.python.run.PythonCommandLineState;
import com.jetbrains.python.run.PythonTracebackFilter;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public abstract class PythonTestCommandLineStateBase extends PythonCommandLineState {
  protected final AbstractPythonRunConfiguration myConfiguration;

  public AbstractPythonRunConfiguration getConfiguration() {
    return myConfiguration;
  }

  public PythonTestCommandLineStateBase(AbstractPythonRunConfiguration configuration, ExecutionEnvironment env) {
    super(configuration, env, Collections.<Filter>emptyList());
    myConfiguration = configuration;
  }

  @NotNull
  protected ConsoleView createAndAttachConsole(Project project, ProcessHandler processHandler, Executor executor)
    throws ExecutionException {

    final PythonTRunnerConsoleProperties consoleProperties = new PythonTRunnerConsoleProperties(myConfiguration, executor);

    if (isDebug()) {
      final ConsoleView testsOutputConsoleView = SMTestRunnerConnectionUtil.createConsole(PythonTRunnerConsoleProperties.FRAMEWORK_NAME,
                                                                                      consoleProperties,
                                                                                      getRunnerSettings(),
                                                                                      getConfigurationSettings());
      final ConsoleView consoleView = new PythonDebugLanguageConsoleView(project, PythonSdkType.findSdkByPath(myConfiguration.getInterpreterPath()), testsOutputConsoleView);
      consoleView.addMessageFilter(new PythonTracebackFilter(project, myConfiguration.getWorkingDirectory()));
      consoleView.attachToProcess(processHandler);
      return consoleView;
    }
    final ConsoleView consoleView = SMTestRunnerConnectionUtil.createAndAttachConsole(PythonTRunnerConsoleProperties.FRAMEWORK_NAME,
                                                                                      processHandler,
                                                                                      consoleProperties,
                                                                                      getRunnerSettings(),
                                                                                      getConfigurationSettings());
    consoleView.addMessageFilter(new PythonTracebackFilter(project, myConfiguration.getWorkingDirectory()));
    return consoleView;
  }

  public GeneralCommandLine generateCommandLine() throws ExecutionException {
    GeneralCommandLine cmd = super.generateCommandLine();

    if (!StringUtil.isEmptyOrSpaces(myConfiguration.getWorkingDirectory())) {
      cmd.setWorkDirectory(myConfiguration.getWorkingDirectory());
    }

    ParamsGroup exe_options = cmd.getParametersList().getParamsGroup(GROUP_EXE_OPTIONS);
    assert exe_options != null;
    exe_options.addParametersString(myConfiguration.getInterpreterOptions());
    addTestRunnerParameters(cmd);

    return cmd;
  }

  @Override
  public ExecutionResult execute(Executor executor, CommandLinePatcher... patchers) throws ExecutionException {
    final ProcessHandler processHandler = startProcess(patchers);
    final ConsoleView console = createAndAttachConsole(myConfiguration.getProject(), processHandler, executor);

    List<AnAction> actions = Lists
      .newArrayList(createActions(console, processHandler));

    DefaultExecutionResult executionResult =
      new DefaultExecutionResult(console, processHandler, actions.toArray(new AnAction[actions.size()]));

    PyRerunFailedTestsAction rerunFailedTestsAction = new PyRerunFailedTestsAction(console);
    if (console instanceof SMTRunnerConsoleView) {
      rerunFailedTestsAction.init(((BaseTestsOutputConsoleView)console).getProperties(), getEnvironment());
      rerunFailedTestsAction.setModelProvider(new Getter<TestFrameworkRunningModel>() {
      @Override
      public TestFrameworkRunningModel get() {
        return ((SMTRunnerConsoleView)console).getResultsViewer();
      }
    });
    }

    executionResult.setRestartActions(rerunFailedTestsAction, new ToggleAutoTestAction());
    return executionResult;
  }

  protected void addBeforeParameters(GeneralCommandLine cmd) throws ExecutionException {}
  protected void addAfterParameters(GeneralCommandLine cmd) throws ExecutionException {}

  protected void addTestRunnerParameters(GeneralCommandLine cmd) throws ExecutionException {
    ParamsGroup script_params = cmd.getParametersList().getParamsGroup(GROUP_SCRIPT);
    assert script_params != null;
    script_params.addParameter(new File(PythonHelpersLocator.getHelpersRoot(), getRunner()).getAbsolutePath());
    addBeforeParameters(cmd);
    script_params.addParameters(getTestSpecs());
    addAfterParameters(cmd);
  }

  protected abstract String getRunner();
  protected abstract List<String> getTestSpecs();
}
