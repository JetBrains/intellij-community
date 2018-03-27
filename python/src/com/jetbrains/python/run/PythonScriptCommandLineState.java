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
import com.google.common.collect.Maps;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.execution.console.ConsoleExecuteAction;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.filters.UrlFilter;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.terminal.TerminalExecutionConsole;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PathMapper;
import com.intellij.util.io.BaseDataReader;
import com.intellij.util.io.BaseOutputReader;
import com.jetbrains.python.PythonHelper;
import com.jetbrains.python.console.*;
import com.jetbrains.python.console.actions.ShowVarsAction;
import com.jetbrains.python.debugger.PyDebugRunner;
import com.jetbrains.python.sdk.PySdkUtil;
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
      else {
        if (myConfig.isModuleMode()) {
          patchers = ArrayUtil.append(patchers, new CommandLinePatcher() {
            @Override
            public void patchCommandLine(GeneralCommandLine commandLine) {
              ParametersList parametersList = commandLine.getParametersList();
              boolean isModule = PyDebugRunner.patchExeParams(parametersList);
              if (isModule) {
                ParamsGroup moduleParams = parametersList.getParamsGroup(PythonCommandLineState.GROUP_MODULE);
                if (moduleParams != null) {
                  moduleParams.addParameterAt(0, PyDebugRunner.MODULE_PARAM);
                }
              }
            }
          });
        }
      }

      Module module = myConfig.getModule();
      PyConsoleOptions.PyConsoleSettings settingsProvider = PyConsoleOptions.getInstance(project).getPythonConsoleSettings();
      PathMapper pathMapper = PydevConsoleRunner.getPathMapper(project, myConfig.getSdk(), settingsProvider);
      String workingDir = myConfig.getWorkingDirectory();
      if (StringUtil.isEmptyOrSpaces(workingDir)) {
        workingDir = PydevConsoleRunnerFactory.getWorkingDir(project, module, pathMapper, settingsProvider);
      }
      String[] setupFragment = PydevConsoleRunnerFactory.createSetupFragment(module, workingDir, pathMapper, settingsProvider);

      if (myConfig.getSdk() == null) {
        throw new ExecutionException("Cannot find SDK for Run configuration " + myConfig.getName());
      }

      Map<String, String> unitedEnvs = Maps.newHashMap(settingsProvider.getEnvs());
      unitedEnvs.putAll(myConfig.getEnvs());
      PydevConsoleRunnerFactory.putIPythonEnvFlag(project, unitedEnvs);

      PythonScriptWithConsoleRunner runner =
        new PythonScriptWithConsoleRunner(project, myConfig.getSdk(), PyConsoleType.PYTHON, workingDir,
                                          unitedEnvs, patchers,
                                          settingsProvider,
                                          setupFragment);
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
      return super.execute(executor, processStarter, patchers);
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

      moduleParameters.addParameter("-m");
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
                                                   @Nullable String workingDir,
                                                   @NotNull int[] ports) {
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
