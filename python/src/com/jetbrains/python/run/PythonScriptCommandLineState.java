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
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.terminal.TerminalExecutionConsole;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PathMapper;
import com.intellij.util.io.BaseDataReader;
import com.intellij.util.io.BaseOutputReader;
import com.jetbrains.python.actions.PyExecuteSelectionAction;
import com.jetbrains.python.console.PyConsoleOptions;
import com.jetbrains.python.console.PydevConsoleRunner;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonEnvUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Map;

/**
 * @author yole
 */
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

      if (executor.getId() == DefaultDebugExecutor.EXECUTOR_ID) {
        return super.execute(executor, processStarter, ArrayUtil.append(patchers, new CommandLinePatcher() {
          @Override
          public void patchCommandLine(GeneralCommandLine commandLine) {
            commandLine.getParametersList().getParamsGroup(PythonCommandLineState.GROUP_DEBUGGER).addParameterAt(1, "--cmd-line");
          }
        }));
      }

      final String runFileText = buildScriptWithConsoleRun();
      if (PyExecuteSelectionAction.canFindConsole(project, myConfig.getSdkHome())) {
        // there are existing consoles, don't care about Rerun action
        PyExecuteSelectionAction.selectConsoleAndExecuteCode(project, runFileText);
      }
      else {
        PyExecuteSelectionAction.startNewConsoleInstance(project, codeExecutor ->
          PyExecuteSelectionAction.executeInConsole(codeExecutor, runFileText, null), runFileText, myConfig);
      }

      return null;
    }
    else if (emulateTerminal()) {
      setRunWithPty(true);

      final ProcessHandler processHandler = startProcess(processStarter, patchers);

      TerminalExecutionConsole executeConsole = new TerminalExecutionConsole(myConfig.getProject(), processHandler);

      executeConsole.addMessageFilter(myConfig.getProject(), new PythonTracebackFilter(myConfig.getProject()));
      executeConsole.addMessageFilter(myConfig.getProject(), new UrlFilter());

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
        public Result applyFilter(String line, int entireLength) {
          int position = line.indexOf(INPUT_FILE_MESSAGE);
          if (position >= 0) {
            VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(filePath));
            if (file == null) {
              return null;
            }
            return new Result(entireLength - filePath.length() - 1, entireLength, new OpenFileHyperlinkInfo(new OpenFileDescriptor(project, file)));
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
    return myConfig.emulateTerminal() && !PySdkUtil.isRemote(getSdk());
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

            @Override
            public boolean withSeparators() {
              return true;
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

    final String scriptOptionsString = myConfig.getScriptParameters();
    if (scriptOptionsString != null) scriptParameters.addParametersString(scriptOptionsString);

    if (!StringUtil.isEmptyOrSpaces(myConfig.getWorkingDirectory())) {
      commandLine.setWorkDirectory(myConfig.getWorkingDirectory());
    }
    String inputFile = myConfig.getInputFile();
    if (myConfig.isRedirectInput() && !StringUtil.isEmptyOrSpaces(inputFile)) {
      commandLine.withInput(new File(inputFile));
    }
  }

  private static String escape(String s) {
    return StringUtil.escapeCharCharacters(s);
  }

  private String buildScriptWithConsoleRun() {
    StringBuilder sb = new StringBuilder();
    final Map<String, String> configEnvs = myConfig.getEnvs();
    configEnvs.remove(PythonEnvUtil.PYTHONUNBUFFERED);
    if (configEnvs.size() > 0) {
      sb.append("import os\n");
      for (Map.Entry<String, String> entry : configEnvs.entrySet()) {
        sb.append("os.environ['").append(escape(entry.getKey())).append("'] = '").append(escape(entry.getValue())).append("'\n");
      }
    }

    final Project project = myConfig.getProject();
    final Sdk sdk = myConfig.getSdk();
    final PathMapper pathMapper =
      PydevConsoleRunner.getPathMapper(project, sdk, PyConsoleOptions.getInstance(project).getPythonConsoleSettings());

    String scriptPath = myConfig.getScriptName();
    String workingDir = myConfig.getWorkingDirectory();
    if (PySdkUtil.isRemote(sdk) && pathMapper != null) {
      scriptPath = pathMapper.convertToRemote(scriptPath);
      workingDir = pathMapper.convertToRemote(workingDir);
    }

    sb.append("runfile('").append(escape(scriptPath)).append("'");

    String scriptParameters = myConfig.getScriptParameters();
    if (!scriptParameters.isEmpty()) {
      sb.append(", args='").append(escape(scriptParameters)).append("'");
    }

    if (!workingDir.isEmpty()) {
      sb.append(", wdir='").append(escape(workingDir)).append("'");
    }

    if (myConfig.isModuleMode()) {
      sb.append(", is_module=True");
    }

    sb.append(")");
    return sb.toString();
  }
}
