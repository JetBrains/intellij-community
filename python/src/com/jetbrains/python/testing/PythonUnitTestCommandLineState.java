package com.jetbrains.python.testing;

import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Leonid Shalupov
 */
public class PythonUnitTestCommandLineState extends CommandLineState {
  private PythonUnitTestRunConfiguration myConfig;

  public PythonUnitTestRunConfiguration getConfig() {
    return myConfig;
  }

  public PythonUnitTestCommandLineState(PythonUnitTestRunConfiguration runConfiguration, ExecutionEnvironment env) {
    super(env);
    myConfig = runConfiguration;
  }

  @Override
  public ExecutionResult execute(@NotNull Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
    final ProcessHandler processHandler = startProcess();
    final ConsoleView console = createAndAttachConsole(getConfig().getProject(), processHandler);

    return new DefaultExecutionResult(console, processHandler, createActions(console, processHandler));
  }

  protected OSProcessHandler startProcess() throws ExecutionException {
    GeneralCommandLine commandLine = generateCommandLine();

    final OSProcessHandler processHandler = new OSProcessHandler(commandLine.createProcess(), commandLine.getCommandLineString());
    ProcessTerminatedListener.attach(processHandler);
    return processHandler;
  }

  private GeneralCommandLine generateCommandLine() {
    GeneralCommandLine commandLine = new GeneralCommandLine();

    commandLine.setExePath(PythonSdkType.getInterpreterPath(myConfig.getSdkHome()));

    commandLine.getParametersList().addParametersString(myConfig.getInterpreterOptions());

    if (!StringUtil.isEmptyOrSpaces(myConfig.getScriptName())) {
      commandLine.addParameter(myConfig.getScriptName());
    }

    //commandLine.getParametersList().addParametersString(myConfig.getScriptName());

    if (!StringUtil.isEmptyOrSpaces(myConfig.getWorkingDirectory())) {
      commandLine.setWorkDirectory(myConfig.getWorkingDirectory());
    }

    commandLine.setEnvParams(myConfig.getEnvs());
    commandLine.setPassParentEnvs(myConfig.isPassParentEnvs());
    return commandLine;
  }

  @NotNull
  protected ConsoleView createAndAttachConsole(Project project, ProcessHandler processHandler) throws ExecutionException {
    return SMTestRunnerConnectionUtil.attachRunner(project, processHandler, this, getConfig(), "PythonUnitTestRunner.Splitter.Proportion");
  }
}
