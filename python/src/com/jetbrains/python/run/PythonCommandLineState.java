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
import com.intellij.execution.process.ColoredProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.HashMap;
import com.jetbrains.python.sdk.PythonSdkFlavor;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * @author Leonid Shalupov
 */
public abstract class PythonCommandLineState extends CommandLineState {
  private final AbstractPythonRunConfiguration myConfig;
  private final List<Filter> myFilters;

  public AbstractPythonRunConfiguration getConfig() {
    return myConfig;
  }

  public PythonCommandLineState(AbstractPythonRunConfiguration runConfiguration, ExecutionEnvironment env, List<Filter> filters) {
    super(env);
    myConfig = runConfiguration;
    myFilters = filters;
  }

  @Override
  public ExecutionResult execute(@NotNull Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
    return execute(null);
  }

  public ExecutionResult execute(CommandLinePatcher... patchers) throws ExecutionException {
    final ProcessHandler processHandler = startProcess(patchers);
    final ConsoleView console = createAndAttachConsole(getConfig().getProject(), processHandler);

    return new DefaultExecutionResult(console, processHandler, createActions(console, processHandler));
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

  protected ColoredProcessHandler startProcess() throws ExecutionException {
    return startProcess(null);
  }

  /**
   * Patches the command line parameters applying patchers from first to last, and then runs it.
   * @param patchers any number of patchers; any patcher may be null, and the whole argument may be null.
   * @return handler of the started process
   * @throws ExecutionException
   */
  protected ColoredProcessHandler startProcess(CommandLinePatcher... patchers) throws ExecutionException {
    GeneralCommandLine commandLine = generateCommandLine();
    if (patchers != null) {
      for (CommandLinePatcher patcher: patchers) {
        if (patcher != null) patcher.patchCommandLine(commandLine);
      }
    }

    final ColoredProcessHandler processHandler = createProcess(commandLine.createProcess(), commandLine);
    ProcessTerminatedListener.attach(processHandler);
    return processHandler;
  }

  protected ColoredProcessHandler createProcess(Process process, GeneralCommandLine commandLine) throws ExecutionException {
    return new ColoredProcessHandler(process, commandLine.getCommandLineString());
  }

  protected GeneralCommandLine generateCommandLine() throws ExecutionException {
    GeneralCommandLine commandLine = new GeneralCommandLine();

    setRunnerPath(commandLine);

    buildCommandLineParameters(commandLine);

    initEnvironment(commandLine);
    return commandLine;
  }

  protected void initEnvironment(GeneralCommandLine commandLine) {
    Map<String, String> envs = myConfig.getEnvs();
    if (envs == null)
      envs = new HashMap<String, String>();
    else
      envs = new HashMap<String, String>(envs);

    addPredefinedEnvironmentVariables(envs);
    commandLine.setEnvParams(envs);
    commandLine.setPassParentEnvs(myConfig.isPassParentEnvs());
  }

  protected void addPredefinedEnvironmentVariables(Map<String, String> envs) {
    final PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(myConfig.getInterpreterPath());
    if (flavor != null) {
      flavor.addPredefinedEnvironmentVariables(envs);
    }
  }

  protected void setRunnerPath(GeneralCommandLine commandLine) throws ExecutionException {
    String interpreterPath = getInterpreterPath();
    commandLine.setExePath(interpreterPath);
  }

  protected String getInterpreterPath() throws ExecutionException {
    String interpreterPath = myConfig.getInterpreterPath();
    if (interpreterPath == null) {
      throw new ExecutionException("Cannot find Python interpreter for this run configuration");
    }
    return interpreterPath;
  }

  protected void buildCommandLineParameters(GeneralCommandLine commandLine) {
  }

  public int getInterpreterOptionsCount() {
    return 0;    
  }
}
