// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.terminal.TerminalExecutionConsole;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.util.io.BaseDataReader;
import com.intellij.util.io.BaseOutputReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.sh.ShStringUtil.quote;

public class ShRunConfigurationProfileState implements RunProfileState {
  private final Project myProject;
  private final ShRunConfiguration myRunConfiguration;

  public ShRunConfigurationProfileState(@NotNull Project project, @NotNull ShRunConfiguration runConfiguration) {
    myProject = project;
    myRunConfiguration = runConfiguration;
  }

  @Nullable
  @Override
  public ExecutionResult execute(Executor executor, @NotNull ProgramRunner<?> runner) throws ExecutionException {
    ShRunner shRunner = ServiceManager.getService(myProject, ShRunner.class);
    if (shRunner == null || !shRunner.isAvailable(myProject) || isRunBeforeConfig()) {
      return buildExecutionResult();
    }
    shRunner.run(buildCommand(), myRunConfiguration.getScriptWorkingDirectory(), myRunConfiguration.getName());
    return null;
  }

  private ExecutionResult buildExecutionResult() throws ExecutionException {
    GeneralCommandLine commandLine = createCommandLine();
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

          @Override
          public boolean withSeparators() {
            return true;
          }
        };
      }
    };
  }

  @NotNull
  private GeneralCommandLine createCommandLine() throws ExecutionException {
    VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(myRunConfiguration.getScriptPath());
    if (virtualFile == null || virtualFile.getParent() == null) {
      throw new ExecutionException("Cannot determine shell script parent directory");
    }

    final WSLDistribution wslDistribution = ShRunConfiguration.getWSLDistributionIfNeeded(myRunConfiguration.getInterpreterPath(),
                                                                                          myRunConfiguration.getScriptPath());

    PtyCommandLine commandLine = new PtyCommandLine();
    if (!SystemInfo.isWindows || wslDistribution != null) {
      commandLine.getEnvironment().put("TERM", "xterm-256color");
    }
    commandLine.withConsoleMode(false);
    commandLine.withInitialColumns(120);
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
    final WSLDistribution wslDistribution = ShRunConfiguration.getWSLDistributionIfNeeded(myRunConfiguration.getInterpreterPath(),
                                                                                          myRunConfiguration.getScriptPath());
    final List<String> commandLine = new ArrayList<>();


    addIfPresent(commandLine, adaptPathForExecution(myRunConfiguration.getInterpreterPath(), null));
    addIfPresent(commandLine, myRunConfiguration.getInterpreterOptions());
    commandLine.add(adaptPathForExecution(myRunConfiguration.getScriptPath(), wslDistribution));
    addIfPresent(commandLine, myRunConfiguration.getScriptOptions());

    if (wslDistribution != null) {
      return wslDistribution
        .patchCommandLine(new GeneralCommandLine(commandLine), myProject, wslDistribution.getWslPath(myRunConfiguration.getScriptWorkingDirectory()), false)
        .getCommandLineString();
    }
    else {
      return String.join(" ", commandLine);
    }
  }

  private static void addIfPresent(@NotNull List<String> commandLine, @Nullable String options) {
    ContainerUtil.addIfNotNull(commandLine, StringUtil.nullize(options));
  }

  private static String adaptPathForExecution(@NotNull String systemDependentPath,
                                              @Nullable WSLDistribution wslDistribution) {
    if (wslDistribution != null) return quote(wslDistribution.getWslPath(systemDependentPath));
    if (Platform.current() != Platform.WINDOWS) return quote(systemDependentPath);
    String escapedPath = StringUtil.escapeQuotes(systemDependentPath);
    return StringUtil.containsWhitespaces(systemDependentPath) ? StringUtil.QUOTER.fun(escapedPath) : escapedPath;
  }

  private static String convertToWslIfNeeded(@NotNull String path, @Nullable WSLDistribution wslDistribution) {
    return wslDistribution != null ? wslDistribution.getWslPath(path) : path;
  }
}