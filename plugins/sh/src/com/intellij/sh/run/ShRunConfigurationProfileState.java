// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.run;

import com.intellij.execution.*;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.PtyCommandLine;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.process.KillableProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.wsl.WSLDistribution;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.sh.ShBundle;
import com.intellij.sh.ShStringUtil;
import com.intellij.terminal.TerminalExecutionConsole;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.util.io.BaseDataReader;
import com.intellij.util.io.BaseOutputReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class ShRunConfigurationProfileState implements RunProfileState {
  private final Project myProject;
  private final ShRunConfiguration myRunConfiguration;

  ShRunConfigurationProfileState(@NotNull Project project, @NotNull ShRunConfiguration runConfiguration) {
    myProject = project;
    myRunConfiguration = runConfiguration;
  }

  @Override
  public @Nullable ExecutionResult execute(Executor executor, @NotNull ProgramRunner<?> runner) throws ExecutionException {
    if (myRunConfiguration.isExecuteInTerminal() && !isRunBeforeConfig()) {
      ShRunner shRunner = ApplicationManager.getApplication().getService(ShRunner.class);
      if (shRunner != null && shRunner.isAvailable(myProject)) {
        shRunner.run(myProject, buildCommand(), myRunConfiguration.getScriptWorkingDirectory(), myRunConfiguration.getName(),
                     isActivateToolWindow());
        return null;
      }
    }
    return buildExecutionResult();
  }

  private boolean isActivateToolWindow() {
    RunnerAndConfigurationSettings settings = RunManager.getInstance(myProject).findSettings(myRunConfiguration);
    return settings == null || settings.isActivateToolWindowBeforeRun();
  }

  private ExecutionResult buildExecutionResult() throws ExecutionException {
    GeneralCommandLine commandLine;
    if (myRunConfiguration.isExecuteScriptFile()) {
      commandLine = createCommandLineForFile();
    }
    else {
      commandLine = createCommandLineForScript();
    }
    ProcessHandler processHandler = createProcessHandler(commandLine);
    ProcessTerminatedListener.attach(processHandler);
    ConsoleView console = new TerminalExecutionConsole(myProject, processHandler);
    console.attachToProcess(processHandler);
    return new DefaultExecutionResult(console, processHandler);
  }

  @NotNull
  private static ProcessHandler createProcessHandler(GeneralCommandLine commandLine) throws ExecutionException {
    return new KillableProcessHandler(commandLine) {
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

  @NotNull
  private GeneralCommandLine createCommandLineForScript() {
    PtyCommandLine commandLine = new PtyCommandLine();
    commandLine.withConsoleMode(false);
    commandLine.withInitialColumns(120);
    commandLine.withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE);
    commandLine.setWorkDirectory(myRunConfiguration.getScriptWorkingDirectory());
    commandLine.withExePath(ObjectUtils.notNull(ShConfigurationType.getDefaultShell(), "/bin/sh"));
    commandLine.withParameters("-c");
    commandLine.withParameters(myRunConfiguration.getScriptText());
    return commandLine;
  }

  @NotNull
  private GeneralCommandLine createCommandLineForFile() throws ExecutionException {
    VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(myRunConfiguration.getScriptPath());
    if (virtualFile == null || virtualFile.getParent() == null) {
      throw new ExecutionException(ShBundle.message("error.message.cannot.determine.shell.script.parent.directory"));
    }

    final WSLDistribution wslDistribution = ShRunConfiguration.getWSLDistributionIfNeeded(myRunConfiguration.getInterpreterPath(),
                                                                                          myRunConfiguration.getScriptPath());

    PtyCommandLine commandLine = new PtyCommandLine();
    if (!SystemInfo.isWindows || wslDistribution != null) {
      commandLine.getEnvironment().put("TERM", "xterm-256color"); //NON-NLS
    }
    commandLine.withConsoleMode(false);
    commandLine.withInitialColumns(120);
    commandLine.withEnvironment(myRunConfiguration.getEnvData().getEnvs());
    commandLine.withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE);
    commandLine.setWorkDirectory(convertToWslIfNeeded(myRunConfiguration.getScriptWorkingDirectory(), wslDistribution));

    commandLine.setExePath(myRunConfiguration.getInterpreterPath());
    if (StringUtil.isNotEmpty(myRunConfiguration.getInterpreterOptions())) {
      commandLine.addParameters(ParametersListUtil.parse(myRunConfiguration.getInterpreterOptions()));
    }
    commandLine.addParameter(convertToWslIfNeeded(myRunConfiguration.getScriptPath(), wslDistribution));
    if (StringUtil.isNotEmpty(myRunConfiguration.getScriptOptions())) {
      commandLine.addParameters(ParametersListUtil.parse(myRunConfiguration.getScriptOptions()));
    }

    if (wslDistribution != null) {
      commandLine = wslDistribution.patchCommandLine(commandLine, myProject, null, false);
    }

    return commandLine;
  }

  private boolean isRunBeforeConfig() {
    Key<Boolean> userDataKey = ShBeforeRunProviderDelegate.getRunBeforeUserDataKey(myRunConfiguration);
    Boolean userDataValue = myProject.getUserData(userDataKey);
    boolean isRunBeforeConfig = userDataValue != null && userDataValue.booleanValue();
    myRunConfiguration.getProject().putUserData(userDataKey, false);
    return isRunBeforeConfig;
  }

  @NotNull
  private String buildCommand() {
    if (myRunConfiguration.isExecuteScriptFile()) {
      final WSLDistribution wslDistribution = ShRunConfiguration.getWSLDistributionIfNeeded(myRunConfiguration.getInterpreterPath(),
                                                                                            myRunConfiguration.getScriptPath());
      final List<String> commandLine = new ArrayList<>();
      addIfPresent(commandLine, myRunConfiguration.getEnvData().getEnvs());
      addIfPresent(commandLine, adaptPathForExecution(myRunConfiguration.getInterpreterPath(), null));
      addIfPresent(commandLine, myRunConfiguration.getInterpreterOptions());
      commandLine.add(adaptPathForExecution(myRunConfiguration.getScriptPath(), wslDistribution));
      addIfPresent(commandLine, myRunConfiguration.getScriptOptions());

      if (wslDistribution != null) {
        return wslDistribution
          .patchCommandLine(new GeneralCommandLine(commandLine), myProject,
                            wslDistribution.getWslPath(myRunConfiguration.getScriptWorkingDirectory()), false)
          .getCommandLineString();
      }
      else {
        return String.join(" ", commandLine);
      }
    }
    else {
      List<String> commandLine = new ArrayList<>();
      addIfPresent(commandLine, myRunConfiguration.getEnvData().getEnvs(), true);
      addIfPresent(commandLine, myRunConfiguration.getScriptText());
      return String.join(" ", commandLine);
    }
  }

  private static void addIfPresent(@NotNull List<String> commandLine, @Nullable String options) {
    ContainerUtil.addIfNotNull(commandLine, StringUtil.nullize(options));
  }

  private static void addIfPresent(@NotNull List<String> commandLine, @NotNull Map<String, String> envs) {
    addIfPresent(commandLine, envs, false);
  }

  private static void addIfPresent(@NotNull List<String> commandLine, @NotNull Map<String, String> envs, boolean endWithSemicolon) {
    int index = 0;
    for (Map.Entry<String, String> entry : envs.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      String quotedString;
      if (Platform.current() != Platform.WINDOWS) {
        quotedString = ShStringUtil.quote(value);
      }
      else {
        String escapedValue = StringUtil.escapeQuotes(value);
        quotedString = StringUtil.containsWhitespaces(value) ? StringUtil.QUOTER.apply(escapedValue) : escapedValue;
      }
      if (endWithSemicolon) {
        String semicolon = "";
        if (index == envs.size() - 1) semicolon = ";";
        commandLine.add("export " + key + "=" + quotedString + semicolon);
      }
      else {
        commandLine.add(key + "=" + quotedString);
      }
      index++;
    }
  }

  private static String adaptPathForExecution(@NotNull String systemDependentPath,
                                              @Nullable WSLDistribution wslDistribution) {
    if (wslDistribution != null) return ShStringUtil.quote(wslDistribution.getWslPath(systemDependentPath));
    if (Platform.current() != Platform.WINDOWS) return ShStringUtil.quote(systemDependentPath);
    String escapedPath = StringUtil.escapeQuotes(systemDependentPath);
    return StringUtil.containsWhitespaces(systemDependentPath) ? StringUtil.QUOTER.apply(escapedPath) : escapedPath;
  }

  private static String convertToWslIfNeeded(@NotNull String path, @Nullable WSLDistribution wslDistribution) {
    return wslDistribution != null ? wslDistribution.getWslPath(path) : path;
  }
}