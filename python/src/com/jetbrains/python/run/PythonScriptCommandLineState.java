// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run;

import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.execution.filters.UrlFilter;
import com.intellij.execution.process.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.target.TargetEnvironment;
import com.intellij.execution.target.TargetEnvironmentRequest;
import com.intellij.execution.target.value.TargetEnvironmentFunctions;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.util.ProgramParametersConfigurator;
import com.intellij.execution.util.ProgramParametersUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.terminal.TerminalExecutionConsole;
import com.intellij.util.ArrayUtil;
import com.intellij.util.io.BaseOutputReader;
import com.jetbrains.python.actions.PyExecuteInConsole;
import com.jetbrains.python.actions.PyRunFileInConsoleAction;
import com.jetbrains.python.console.PyConsoleOptions;
import com.jetbrains.python.run.target.HelpersAwareTargetEnvironmentRequest;
import com.jetbrains.python.run.target.PySdkTargetPaths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;


public class PythonScriptCommandLineState extends PythonCommandLineState {
  private static final String INPUT_FILE_MESSAGE = "Input is being redirected from ";
  private final PythonRunConfiguration myConfig;

  public PythonScriptCommandLineState(PythonRunConfiguration runConfiguration, ExecutionEnvironment env) {
    super(runConfiguration, env);
    myConfig = runConfiguration;
  }

  @Override
  public @Nullable ExecutionResult execute(Executor executor,
                                           PythonProcessStarter processStarter,
                                           CommandLinePatcher... patchers) throws ExecutionException {
    Project project = myConfig.getProject();

    if (myConfig.showCommandLineAfterwards() && !emulateTerminal()) {
      if (!DefaultDebugExecutor.EXECUTOR_ID.equals(executor.getId()) && !DefaultRunExecutor.EXECUTOR_ID.equals(executor.getId())) {
        // disable "Show command line" for all executors except of Run and Debug, because it's useless
        return super.execute(executor, processStarter, patchers);
      }

      PyRunFileInConsoleAction.configExecuted(myConfig);

      if (DefaultDebugExecutor.EXECUTOR_ID.equals(executor.getId())) {
        return super.execute(executor, processStarter, ArrayUtil.append(patchers, new CommandLinePatcher() {
          @Override
          public void patchCommandLine(GeneralCommandLine commandLine) {
            commandLine.getParametersList().getParamsGroup(GROUP_DEBUGGER).addParameterAt(1, "--cmd-line");
          }
        }));
      }

      final String runFileText = PythonConsoleScripts.buildScriptWithConsoleRun(myConfig);
      final boolean useExistingConsole = PyConsoleOptions.getInstance(project).isUseExistingConsole();
      ApplicationManager.getApplication().invokeLater(() -> {
        PyExecuteInConsole.executeCodeInConsole(project, runFileText, null, useExistingConsole, false, true, myConfig);
      });
      return null;
    }
    else if (emulateTerminal()) {
      setRunWithPty(true);

      final ProcessHandler processHandler = startProcess(processStarter, patchers);

      TerminalExecutionConsole executeConsole = createTerminalExecutionConsole(myConfig.getProject(), processHandler);

      processHandler.startNotify();

      return new DefaultExecutionResult(executeConsole, processHandler, AnAction.EMPTY_ARRAY);
    }
    else {
      ExecutionResult executionResult = super.execute(executor, processStarter, patchers);
      if (myConfig.isRedirectInput()) {
        addInputRedirectionMessage(project, executionResult);
      }
      return executionResult;
    }
  }

  @Override
  public @Nullable ExecutionResult execute(@NotNull Executor executor, @NotNull PythonScriptTargetedCommandLineBuilder converter)
    throws ExecutionException {
    Project project = myConfig.getProject();
    if (showCommandLineAfterwards()) {
      if (DefaultRunExecutor.EXECUTOR_ID.equals(executor.getId())) {
        PyRunFileInConsoleAction.configExecuted(myConfig);

        Function<TargetEnvironment, String> runFileText = PythonConsoleScripts.buildScriptFunctionWithConsoleRun(myConfig);
        boolean useExistingConsole = PyConsoleOptions.getInstance(project).isUseExistingConsole();
        PyExecuteInConsole.executeCodeInConsole(project, runFileText, null, useExistingConsole, false, true, myConfig);

        return null;
      }
      else {
        if (DefaultDebugExecutor.EXECUTOR_ID.equals(executor.getId())) {
          PyRunFileInConsoleAction.configExecuted(myConfig);
        }
        return super.execute(executor, converter);
      }
    }
    else if (emulateTerminal()) {
      setRunWithPty(true);

      ProcessHandler processHandler = startProcess(converter);

      TerminalExecutionConsole executeConsole = createTerminalExecutionConsole(project, processHandler);

      processHandler.startNotify();

      return new DefaultExecutionResult(executeConsole, processHandler, AnAction.EMPTY_ARRAY);
    }
    else {
      ExecutionResult executionResult = super.execute(executor, converter);
      if (myConfig.isRedirectInput()) {
        addInputRedirectionMessage(project, executionResult);
      }
      return executionResult;
    }
  }

  private static @NotNull TerminalExecutionConsole createTerminalExecutionConsole(@NotNull Project project,
                                                                                  @NotNull ProcessHandler processHandler) {
    TerminalExecutionConsole executeConsole = new TerminalExecutionConsole(project, processHandler);
    executeConsole.addMessageFilter(new PythonTracebackFilter(project));
    executeConsole.addMessageFilter(new UrlFilter());
    return executeConsole;
  }

  private void addInputRedirectionMessage(@NotNull Project project, @NotNull ExecutionResult executionResult) {
    final String filePath = FileUtil.toSystemDependentName(new File(myConfig.getInputFile()).getAbsolutePath());
    final ProcessHandler processHandler = executionResult.getProcessHandler();
    processHandler.addProcessListener(new ProcessListener() {
      @Override
      public void startNotified(@NotNull ProcessEvent event) {
        processHandler.notifyTextAvailable(INPUT_FILE_MESSAGE + filePath + "\n", ProcessOutputTypes.SYSTEM);
      }

      @Override
      public void processTerminated(@NotNull ProcessEvent event) {
        processHandler.removeProcessListener(this);
      }
    });

    final ExecutionConsole console = executionResult.getExecutionConsole();
    if (console instanceof ConsoleView) {
      ((ConsoleView)console).addMessageFilter(new Filter() {
        @Override
        public @Nullable Result applyFilter(@NotNull String line, int entireLength) {
          int position = line.indexOf(INPUT_FILE_MESSAGE);
          if (position >= 0) {
            VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(filePath));
            if (file == null) {
              return null;
            }
            return new Result(entireLength - filePath.length() - 1, entireLength,
                              new OpenFileHyperlinkInfo(new OpenFileDescriptor(project, file)));
          }
          return null;
        }
      });
    }
  }

  public final boolean showCommandLineAfterwards() {
    return myConfig.showCommandLineAfterwards() && !emulateTerminal();
  }

  /**
   * {@link PythonRunConfiguration#emulateTerminal()} setting might stick from
   * the Python Run configuration with a local interpreter used and running
   * configuration with an interpreter later changed to the remote will fail
   * with <cite>Works currently only with OSProcessHandler</cite> error.
   *
   * @return effective emulate terminal configuration option
   * @see com.intellij.terminal.ProcessHandlerTtyConnector
   */
  private boolean emulateTerminal() {
    return myConfig.emulateTerminal();
  }

  @Override
  public void customizeEnvironmentVars(Map<String, String> envs, boolean passParentEnvs) {
    super.customizeEnvironmentVars(envs, passParentEnvs);
    if (emulateTerminal()) {
      if (!SystemInfo.isWindows) {
        envs.put("TERM", "xterm-256color");
      }
    }
  }

  @Override
  protected void customizePythonExecutionEnvironmentVars(@NotNull HelpersAwareTargetEnvironmentRequest helpersAwareTargetRequest,
                                                         @NotNull Map<String, Function<TargetEnvironment, String>> envs,
                                                         boolean passParentEnvs) {
    super.customizePythonExecutionEnvironmentVars(helpersAwareTargetRequest, envs, passParentEnvs);
    if (emulateTerminal()) {
      if (!SystemInfo.isWindows) {
        envs.put("TERM", TargetEnvironmentFunctions.constant("xterm-256color"));
      }
    }
  }

  @Override
  protected ProcessHandler doCreateProcess(GeneralCommandLine commandLine) throws ExecutionException {
    if (emulateTerminal()) {
      return new OSProcessHandler(commandLine) {
        @Override
        protected @NotNull BaseOutputReader.Options readerOptions() {
          return BaseOutputReader.Options.forTerminalPtyProcess();
        }
      };
    }
    else {
      return super.doCreateProcess(commandLine);
    }
  }

  @Override
  protected @NotNull PythonExecution buildPythonExecution(@NotNull HelpersAwareTargetEnvironmentRequest helpersAwareRequest) {
    TargetEnvironmentRequest targetEnvironmentRequest = helpersAwareRequest.getTargetEnvironmentRequest();
    PythonExecution pythonExecution;
    if (myConfig.isModuleMode()) {
      PythonModuleExecution moduleExecution = new PythonModuleExecution();
      String moduleName = myConfig.getScriptName();
      if (!StringUtil.isEmptyOrSpaces(moduleName)) {
        moduleExecution.setModuleName(moduleName);
      }
      pythonExecution = moduleExecution;
    }
    else {
      PythonScriptExecution pythonScriptExecution = new PythonScriptExecution();
      String scriptPath = myConfig.getScriptName();
      if (!StringUtil.isEmptyOrSpaces(scriptPath)) {
        scriptPath = getExpandedScriptName(myConfig);
        pythonScriptExecution.setPythonScriptPath(getTargetPath(targetEnvironmentRequest, Path.of(scriptPath)));
      }
      pythonExecution = pythonScriptExecution;
    }

    pythonExecution.addParameters(getExpandedScriptParameters(myConfig));

    pythonExecution.setCharset(EncodingProjectManager.getInstance(myConfig.getProject()).getDefaultCharset());

    return pythonExecution;
  }

  @Override
  protected void buildCommandLineParameters(GeneralCommandLine commandLine) {
    ParametersList parametersList = commandLine.getParametersList();
    ParamsGroup exeOptions = parametersList.getParamsGroup(GROUP_EXE_OPTIONS);
    assert exeOptions != null;
    exeOptions.addParametersString(myConfig.getInterpreterOptions());

    ParamsGroup scriptParameters = parametersList.getParamsGroup(GROUP_SCRIPT);
    assert scriptParameters != null;

    if (myConfig.isModuleMode()) {
      ParamsGroup moduleParameters = parametersList.getParamsGroup(GROUP_MODULE);
      assert moduleParameters != null;

      moduleParameters.addParameter(MODULE_PARAMETER);
      moduleParameters.addParameters(myConfig.getScriptName());
    }
    else {
      if (!StringUtil.isEmptyOrSpaces(myConfig.getScriptName())) {
        scriptParameters.addParameter(getExpandedScriptName(myConfig));
      }
    }

    scriptParameters.addParameters(getExpandedScriptParameters(myConfig));

    if (!StringUtil.isEmptyOrSpaces(myConfig.getWorkingDirectory())) {
      commandLine.setWorkDirectory(getExpandedWorkingDir(myConfig));
    }
    String inputFile = myConfig.getInputFile();
    if (myConfig.isRedirectInput() && !StringUtil.isEmptyOrSpaces(inputFile)) {
      commandLine.withInput(new File(inputFile));
    }
  }

  @Override
  protected @NotNull Function<TargetEnvironment, String> getTargetPath(@NotNull TargetEnvironmentRequest targetEnvironmentRequest,
                                                                       @NotNull Path scriptPath) {
    return PySdkTargetPaths.getTargetPathForPythonConsoleExecution(myConfig.getProject(), myConfig.getSdk(), createRemotePathMapper(),
                                                                   scriptPath);
  }

  private static @NotNull List<String> getExpandedScriptParameters(@NotNull PythonRunConfiguration config) {
    final String parameters = config.getScriptParameters();
    return ProgramParametersConfigurator.expandMacrosAndParseParameters(parameters);
  }

  public static @NotNull String getExpandedWorkingDir(@NotNull AbstractPythonRunConfiguration config) {
    final String workingDirectory = config.getWorkingDirectory();
    return ProgramParametersUtil.expandPathAndMacros(workingDirectory, config.getModule(), config.getProject());
  }

  public static @NotNull String getExpandedScriptName(@NotNull PythonRunConfiguration config) {
    final String scriptName = config.getScriptName();
    return ProgramParametersUtil.expandPathAndMacros(scriptName, config.getModule(), config.getProject());
  }
}
