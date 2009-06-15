package com.jetbrains.python.run;

import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Leonid Shalupov
 */
public class PythonCommandLineState extends CommandLineState {
  private PythonRunConfiguration myConfig;
  private final List<Filter> myFilters;

  public PythonRunConfiguration getConfig() {
    return myConfig;
  }

  public PythonCommandLineState(PythonRunConfiguration runConfiguration, ExecutionEnvironment env, List<Filter> filters) {
    super(env);
    myConfig = runConfiguration;
    myFilters = filters;
  }

  @Override
  public ExecutionResult execute(@NotNull Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
    final ProcessHandler processHandler = startProcess();
    final ConsoleView console = createAndAttachConsole(getConfig().getProject(), processHandler);

    return new DefaultExecutionResult(console, processHandler,
                                      createActions(console, processHandler, executor));
  }

  @NotNull
  protected ConsoleView createAndAttachConsole(Project project, ProcessHandler processHandler) throws ExecutionException {
    final TextConsoleBuilder consoleBuilder = TextConsoleBuilderFactory.getInstance().createBuilder(project);
    for (Filter filter : myFilters) {
      consoleBuilder.addFilter(filter);
    }
    
    final ConsoleView consoleView = consoleBuilder.getConsole();
    consoleView.attachToProcess(processHandler);
    return consoleView;
  }

  protected OSProcessHandler startProcess() throws ExecutionException {
    GeneralCommandLine commandLine = generateCommandLine();

    final OSProcessHandler processHandler = new OSProcessHandler(commandLine.createProcess(), commandLine.getCommandLineString());
    ProcessTerminatedListener.attach(processHandler);
    return processHandler;
  }

  private GeneralCommandLine generateCommandLine() {
    GeneralCommandLine commandLine = new GeneralCommandLine();

    commandLine.setExePath(myConfig.getInterpreterPath());

    commandLine.getParametersList().addParametersString(myConfig.getInterpreterOptions());

    if (!StringUtil.isEmptyOrSpaces(myConfig.getScriptName())) {
      commandLine.addParameter(myConfig.getScriptName());
    }

    commandLine.getParametersList().addParametersString(myConfig.getScriptParameters());

    if (!StringUtil.isEmptyOrSpaces(myConfig.getWorkingDirectory())) {
      commandLine.setWorkDirectory(myConfig.getWorkingDirectory());
    }

    commandLine.setEnvParams(myConfig.getEnvs());
    commandLine.setPassParentEnvs(myConfig.isPassParentEnvs());
    return commandLine;
  }
}
