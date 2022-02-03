// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.terminal.TerminalExecutionConsole;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PathMapper;
import com.intellij.util.io.BaseDataReader;
import com.intellij.util.io.BaseOutputReader;
import com.jetbrains.python.actions.PyExecuteInConsole;
import com.jetbrains.python.actions.PyRunFileInConsoleAction;
import com.jetbrains.python.console.PyConsoleOptions;
import com.jetbrains.python.console.PydevConsoleRunner;
import com.jetbrains.python.run.target.HelpersAwareTargetEnvironmentRequest;
import com.jetbrains.python.run.target.PySdkTargetPaths;
import com.jetbrains.python.sdk.PythonEnvUtil;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
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
  @Nullable
  public ExecutionResult execute(Executor executor,
                                 PythonProcessStarter processStarter,
                                 CommandLinePatcher... patchers) throws ExecutionException {
    Project project = myConfig.getProject();

    if (myConfig.showCommandLineAfterwards() && !emulateTerminal()) {
      if (executor.getId() != DefaultDebugExecutor.EXECUTOR_ID && executor.getId() != DefaultRunExecutor.EXECUTOR_ID) {
        // disable "Show command line" for all executors except of Run and Debug, because it's useless
        return super.execute(executor, processStarter, patchers);
      }

      PyRunFileInConsoleAction.configExecuted(myConfig);

      if (executor.getId() == DefaultDebugExecutor.EXECUTOR_ID) {
        return super.execute(executor, processStarter, ArrayUtil.append(patchers, new CommandLinePatcher() {
          @Override
          public void patchCommandLine(GeneralCommandLine commandLine) {
            commandLine.getParametersList().getParamsGroup(PythonCommandLineState.GROUP_DEBUGGER).addParameterAt(1, "--cmd-line");
          }
        }));
      }

      final String runFileText = buildScriptWithConsoleRun(myConfig);
      final boolean useExistingConsole = PyConsoleOptions.getInstance(project).isUseExistingConsole();
      ApplicationManager.getApplication().invokeLater(() -> {
        PyExecuteInConsole.executeCodeInConsole(project, runFileText, null, useExistingConsole, false, true, myConfig);
      });
      return null;
    }
    else if (emulateTerminal()) {
      setRunWithPty(true);

      final ProcessHandler processHandler = startProcess(processStarter, patchers);

      TerminalExecutionConsole executeConsole = new TerminalExecutionConsole(myConfig.getProject(), processHandler);

      executeConsole.addMessageFilter(new PythonTracebackFilter(myConfig.getProject()));
      executeConsole.addMessageFilter(new UrlFilter());

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

  private void addInputRedirectionMessage(@NotNull Project project, @NotNull ExecutionResult executionResult) {
    final String filePath = FileUtil.toSystemDependentName(new File(myConfig.getInputFile()).getAbsolutePath());
    final ProcessHandler processHandler = executionResult.getProcessHandler();
    processHandler.addProcessListener(new ProcessAdapter() {
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
        @Nullable
        @Override
        public Result applyFilter(@NotNull String line, int entireLength) {
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
    return myConfig.emulateTerminal() && !PythonSdkUtil.isRemote(getSdk());
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
  public void customizePythonExecutionEnvironmentVars(@NotNull TargetEnvironmentRequest targetEnvironment,
                                                      @NotNull Map<String, Function<TargetEnvironment, String>> envs,
                                                      boolean passParentEnvs) {
    super.customizePythonExecutionEnvironmentVars(targetEnvironment, envs, passParentEnvs);
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
        @NotNull
        @Override
        protected BaseOutputReader.Options readerOptions() {
          return new BaseOutputReader.Options() {
            @Override
            public BaseDataReader.SleepingPolicy policy() {
              return BaseDataReader.SleepingPolicy.BLOCKING;
            }

            @Override
            public boolean splitToLines() {
              return false;
            }
          };
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
        pythonScriptExecution.setPythonScriptPath(getTargetPath(targetEnvironmentRequest, scriptPath));
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
        scriptParameters.addParameter(myConfig.getScriptName());
      }
    }

    scriptParameters.addParameters(getExpandedScriptParameters(myConfig));

    if (!StringUtil.isEmptyOrSpaces(myConfig.getWorkingDirectory())) {
      commandLine.setWorkDirectory(myConfig.getWorkingDirectory());
    }
    String inputFile = myConfig.getInputFile();
    if (myConfig.isRedirectInput() && !StringUtil.isEmptyOrSpaces(inputFile)) {
      commandLine.withInput(new File(inputFile));
    }
  }

  @Override
  protected @NotNull Function<TargetEnvironment, String> getTargetPath(@NotNull TargetEnvironmentRequest targetEnvironmentRequest,
                                                                       @NotNull String scriptPath) {
    return PySdkTargetPaths.getTargetPathForPythonConsoleExecution(targetEnvironmentRequest, myConfig.getProject(), myConfig.getSdk(),
                                                                   createRemotePathMapper(), scriptPath);
  }

  private static @NotNull List<String> getExpandedScriptParameters(PythonRunConfiguration config) {
    final String parameters = config.getScriptParameters();
    return ProgramParametersConfigurator.expandMacrosAndParseParameters(parameters);
  }

  private static String escape(String s) {
    return StringUtil.escapeCharCharacters(s);
  }

  public static String buildScriptWithConsoleRun(PythonRunConfiguration config) {
    StringBuilder sb = new StringBuilder();
    final Map<String, String> configEnvs = config.getEnvs();
    configEnvs.remove(PythonEnvUtil.PYTHONUNBUFFERED);
    if (configEnvs.size() > 0) {
      sb.append("import os\n");
      for (Map.Entry<String, String> entry : configEnvs.entrySet()) {
        sb.append("os.environ['").append(escape(entry.getKey())).append("'] = '").append(escape(entry.getValue())).append("'\n");
      }
    }

    final Project project = config.getProject();
    final Sdk sdk = config.getSdk();
    final PathMapper pathMapper =
      PydevConsoleRunner.getPathMapper(project, sdk, PyConsoleOptions.getInstance(project).getPythonConsoleSettings());

    String scriptPath = config.getScriptName();
    String workingDir = config.getWorkingDirectory();
    if (PythonSdkUtil.isRemote(sdk) && pathMapper != null) {
      scriptPath = pathMapper.convertToRemote(scriptPath);
      workingDir = pathMapper.convertToRemote(workingDir);
    }

    sb.append("runfile('").append(escape(scriptPath)).append("'");

    final List<String> scriptParameters = getExpandedScriptParameters(config);
    if (scriptParameters.size() != 0) {
      sb.append(", args=[");
      for (int i = 0; i < scriptParameters.size(); i++) {
        if (i != 0) {
          sb.append(", ");
        }
        sb.append("'").append(escape(scriptParameters.get(i))).append("'");
      }
      sb.append("]");
    }

    if (!workingDir.isEmpty()) {
      sb.append(", wdir='").append(escape(workingDir)).append("'");
    }

    if (config.isModuleMode()) {
      sb.append(", is_module=True");
    }

    sb.append(")");
    return sb.toString();
  }
}
