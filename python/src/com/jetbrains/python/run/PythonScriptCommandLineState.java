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

import com.google.common.collect.Lists;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.execution.console.ConsoleExecuteAction;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.filters.UrlFilter;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.terminal.TerminalExecutionConsole;
import com.intellij.util.ArrayUtil;
import com.intellij.util.io.BaseDataReader;
import com.intellij.util.io.BaseOutputReader;
import com.jetbrains.python.PythonHelper;
import com.jetbrains.python.console.PyConsoleOptions;
import com.jetbrains.python.console.PyConsoleType;
import com.jetbrains.python.console.PydevConsoleRunnerImpl;
import com.jetbrains.python.console.actions.ShowVarsAction;
import com.jetbrains.python.sdk.PythonEnvUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

import static com.intellij.execution.runners.AbstractConsoleRunnerWithHistory.registerActionShortcuts;

/**
 * @author yole
 */
public class PythonScriptCommandLineState extends PythonCommandLineState {
  private final PythonRunConfiguration myConfig;

  public PythonScriptCommandLineState(PythonRunConfiguration runConfiguration, ExecutionEnvironment env) {
    super(runConfiguration, env);
    myConfig = runConfiguration;
  }

  @Override
  public ExecutionResult execute(Executor executor,
                                 PythonProcessStarter processStarter,
                                 final CommandLinePatcher... patchers) throws ExecutionException {
    if (myConfig.showCommandLineAfterwards() && !myConfig.emulateTerminal()) {
      if (executor.getId() == DefaultDebugExecutor.EXECUTOR_ID) {
        return super.execute(executor, processStarter, ArrayUtil.append(patchers, new CommandLinePatcher() {
          @Override
          public void patchCommandLine(GeneralCommandLine commandLine) {
            commandLine.getParametersList().getParamsGroup(PythonCommandLineState.GROUP_DEBUGGER).addParameterAt(1, "--cmd-line");
          }
        }));
      }

      PythonScriptWithConsoleRunner runner =
        new PythonScriptWithConsoleRunner(myConfig.getProject(), myConfig.getSdk(), PyConsoleType.PYTHON, myConfig.getWorkingDirectory(),
                                          myConfig.getEnvs(), patchers,
                                          PyConsoleOptions.getInstance(myConfig.getProject()).getPythonConsoleSettings());

      runner.setEnableAfterConnection(false);
      runner.runSync();
      // runner.getProcessHandler() would be null if execution error occurred
      if (runner.getProcessHandler() == null) {
        return null;
      }
      runner.getPydevConsoleCommunication().setConsoleView(runner.getConsoleView());
      List<AnAction> actions = Lists.newArrayList(createActions(runner.getConsoleView(), runner.getProcessHandler()));
      actions.add(new ShowVarsAction(runner.getConsoleView(), runner.getPydevConsoleCommunication()));

      return new DefaultExecutionResult(runner.getConsoleView(), runner.getProcessHandler(), actions.toArray(new AnAction[actions.size()]));
    }
    else if (myConfig.emulateTerminal()) {
      setRunWithPty(true);

      final ProcessHandler processHandler = startProcess(processStarter, patchers);

      TerminalExecutionConsole executeConsole = new TerminalExecutionConsole(myConfig.getProject(), processHandler);

      executeConsole.addMessageFilter(myConfig.getProject(), new PythonTracebackFilter(myConfig.getProject()));
      executeConsole.addMessageFilter(myConfig.getProject(), new UrlFilter());

      processHandler.startNotify();

      return new DefaultExecutionResult(executeConsole, processHandler, AnAction.EMPTY_ARRAY);
    }
    else {
      return super.execute(executor, processStarter, patchers);
    }
  }

  @Override
  public void customizeEnvironmentVars(Map<String, String> envs, boolean passParentEnvs) {
    if (myConfig.emulateTerminal()) {
      if (!SystemInfo.isWindows) {
        envs.put("TERM", "xterm-256color");
      }
    }
  }

  @Override
  public boolean mixedDebugMode() {
    return myConfig.mixedDebugMode();
  }

  @NotNull
  @Override
  public String getDebuggableExternalLibs() {
    return myConfig.getDebuggableExternalLibs();
  }

  @Override
  protected ProcessHandler doCreateProcess(GeneralCommandLine commandLine) throws ExecutionException {
    if (myConfig.emulateTerminal()) {
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
    ParamsGroup exe_options = parametersList.getParamsGroup(GROUP_EXE_OPTIONS);
    assert exe_options != null;
    exe_options.addParametersString(myConfig.getInterpreterOptions());

    ParamsGroup script_parameters = parametersList.getParamsGroup(GROUP_SCRIPT);
    assert script_parameters != null;
    if (!StringUtil.isEmptyOrSpaces(myConfig.getScriptName())) {
      script_parameters.addParameter(myConfig.getScriptName());
    }

    final String script_options_string = myConfig.getScriptParameters();
    if (script_options_string != null) script_parameters.addParametersString(script_options_string);

    if (!StringUtil.isEmptyOrSpaces(myConfig.getWorkingDirectory())) {
      commandLine.setWorkDirectory(myConfig.getWorkingDirectory());
    }
  }

  /**
   * @author traff
   */
  public class PythonScriptWithConsoleRunner extends PydevConsoleRunnerImpl {

    private CommandLinePatcher[] myPatchers;
    private String PYDEV_RUN_IN_CONSOLE_PY = "pydev/pydev_run_in_console.py";

    public PythonScriptWithConsoleRunner(@NotNull Project project,
                                         @NotNull Sdk sdk,
                                         @NotNull PyConsoleType consoleType,
                                         @Nullable String workingDir,
                                         Map<String, String> environmentVariables,
                                         CommandLinePatcher[] patchers,
                                         PyConsoleOptions.PyConsoleSettings consoleSettings,
                                         String... statementsToExecute) {
      super(project, sdk, consoleType, workingDir, environmentVariables, consoleSettings, (s) -> {
      }, statementsToExecute);
      myPatchers = patchers;
    }

    @Override
    protected void createContentDescriptorAndActions() {
      AnAction a = new ConsoleExecuteAction(super.getConsoleView(), myConsoleExecuteActionHandler,
                                            myConsoleExecuteActionHandler.getEmptyExecuteAction(), myConsoleExecuteActionHandler);
      registerActionShortcuts(Lists.newArrayList(a), getConsoleView().getConsoleEditor().getComponent());
    }

    @Override
    protected String getRunnerFileFromHelpers() {
      return PYDEV_RUN_IN_CONSOLE_PY;
    }

    @Override
    protected GeneralCommandLine createCommandLine(@NotNull Sdk sdk,
                                                   @NotNull Map<String, String> environmentVariables,
                                                   String workingDir, int[] ports) {
      GeneralCommandLine consoleCmdLine = doCreateConsoleCmdLine(sdk, environmentVariables, workingDir, ports, PythonHelper.RUN_IN_CONSOLE);

      final GeneralCommandLine cmd = generateCommandLine(myPatchers);

      ParamsGroup group = consoleCmdLine.getParametersList().getParamsGroup(PythonCommandLineState.GROUP_SCRIPT);
      assert group != null;
      group.addParameters(cmd.getParametersList().getList());

      PythonEnvUtil.mergePythonPath(consoleCmdLine.getEnvironment(), cmd.getEnvironment());

      consoleCmdLine.getEnvironment().putAll(cmd.getEnvironment());

      return consoleCmdLine;
    }
  }
}
