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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.eel.EelDescriptor;
import com.intellij.platform.eel.EelPlatform;
import com.intellij.platform.eel.provider.EelNioBridgeServiceKt;
import com.intellij.platform.eel.provider.LocalEelDescriptor;
import com.intellij.sh.ShBundle;
import com.intellij.sh.ShStringUtil;
import com.intellij.terminal.TerminalExecutionConsole;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.util.io.BaseOutputReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.intellij.platform.eel.provider.EelProviderUtil.getEelDescriptor;

final class ShRunConfigurationProfileState implements RunProfileState {
  private final Project myProject;
  private final ShRunConfiguration myRunConfiguration;

  ShRunConfigurationProfileState(@NotNull Project project, @NotNull ShRunConfiguration runConfiguration) {
    myProject = project;
    myRunConfiguration = runConfiguration;
  }

  @Override
  public @Nullable ExecutionResult execute(Executor executor, @NotNull ProgramRunner<?> runner) throws ExecutionException {
    final EelDescriptor eelDescriptor = computeEelDescriptor();

    if (myRunConfiguration.isExecuteInTerminal() && !isRunBeforeConfig()) {
      ShRunner shRunner = ApplicationManager.getApplication().getService(ShRunner.class);
      if (shRunner != null && shRunner.isAvailable(myProject)) {
        shRunner.run(myProject, buildCommand(eelDescriptor), myRunConfiguration.getScriptWorkingDirectory(), myRunConfiguration.getName(),
                     isActivateToolWindow());
        return null;
      }
    }
    return buildExecutionResult(eelDescriptor);
  }

  private boolean isActivateToolWindow() {
    RunnerAndConfigurationSettings settings = RunManager.getInstance(myProject).findSettings(myRunConfiguration);
    return settings == null || settings.isActivateToolWindowBeforeRun() || settings.isFocusToolWindowBeforeRun();
  }

  private ExecutionResult buildExecutionResult(@NotNull EelDescriptor eelDescriptor) throws ExecutionException {
    GeneralCommandLine commandLine;
    if (myRunConfiguration.isExecuteScriptFile()) {
      commandLine = createCommandLineForFile(eelDescriptor);
    }
    else {
      commandLine = createCommandLineForScript(eelDescriptor);
    }
    ProcessHandler processHandler = createProcessHandler(commandLine);
    ProcessTerminatedListener.attach(processHandler);
    ConsoleView console = new TerminalExecutionConsole(myProject, processHandler);
    console.attachToProcess(processHandler);
    return new DefaultExecutionResult(console, processHandler);
  }

  private static @NotNull ProcessHandler createProcessHandler(GeneralCommandLine commandLine) throws ExecutionException {
    return new KillableProcessHandler(commandLine) {
      @Override
      protected @NotNull BaseOutputReader.Options readerOptions() {
        return BaseOutputReader.Options.forTerminalPtyProcess();
      }
    };
  }

  private @NotNull GeneralCommandLine createCommandLineForScript(@NotNull EelDescriptor eelDescriptor) {
    PtyCommandLine commandLine = new PtyCommandLine();
    commandLine.withConsoleMode(false);
    commandLine.withInitialColumns(120);
    commandLine.withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE);
    commandLine.withWorkingDirectory(Path.of(myRunConfiguration.getScriptWorkingDirectory()));
    commandLine.withExePath(convertPathUsingEel(ShConfigurationType.getDefaultShell(myProject), eelDescriptor));
    commandLine.withParameters("-c");
    commandLine.withParameters(myRunConfiguration.getScriptText());
    return commandLine;
  }

  private @NotNull GeneralCommandLine createCommandLineForFile(@NotNull EelDescriptor eelDescriptor) throws ExecutionException {
    VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(myRunConfiguration.getScriptPath());
    if (virtualFile == null || virtualFile.getParent() == null) {
      throw new ExecutionException(ShBundle.message("error.message.cannot.determine.shell.script.parent.directory"));
    }

    PtyCommandLine commandLine = new PtyCommandLine();
    commandLine.withConsoleMode(false);
    commandLine.withInitialColumns(120);
    commandLine.withEnvironment(myRunConfiguration.getEnvData().getEnvs());
    commandLine.withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE);
    commandLine.withWorkingDirectory(Path.of(myRunConfiguration.getScriptWorkingDirectory()));

    commandLine.setExePath(convertPathUsingEel(myRunConfiguration.getInterpreterPath(), eelDescriptor));
    if (StringUtil.isNotEmpty(myRunConfiguration.getInterpreterOptions())) {
      commandLine.addParameters(ParametersListUtil.parse(myRunConfiguration.getInterpreterOptions()));
    }
    commandLine.addParameter(convertPathUsingEel(myRunConfiguration.getScriptPath(), eelDescriptor));
    if (StringUtil.isNotEmpty(myRunConfiguration.getScriptOptions())) {
      commandLine.addParameters(ParametersListUtil.parse(myRunConfiguration.getScriptOptions()));
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

  private @NotNull String buildCommand(@NotNull EelDescriptor eelDescriptor) {
    if (myRunConfiguration.isExecuteScriptFile()) {
      final List<String> commandLine = new ArrayList<>();
      addIfPresent(commandLine, myRunConfiguration.getEnvData().getEnvs());
      addIfPresent(commandLine, adaptPathForExecution(myRunConfiguration.getInterpreterPath(), eelDescriptor));
      addIfPresent(commandLine, myRunConfiguration.getInterpreterOptions());
      commandLine.add(adaptPathForExecution(myRunConfiguration.getScriptPath(), eelDescriptor));
      addIfPresent(commandLine, myRunConfiguration.getScriptOptions());
      return String.join(" ", commandLine);
    }
    else {
      List<String> commandLine = new ArrayList<>();
      addIfPresent(commandLine, myRunConfiguration.getEnvData().getEnvs(), true);
      addIfPresent(commandLine, myRunConfiguration.getScriptText());
      return String.join(" ", commandLine);
    }
  }

  private EelDescriptor computeEelDescriptor() {
    EelDescriptor eelDescriptor = null;

    if (!myRunConfiguration.getScriptWorkingDirectory().isEmpty()) {
      eelDescriptor = nullizeIfLocal(getEelDescriptor(Path.of(myRunConfiguration.getScriptWorkingDirectory())));
    }

    if (eelDescriptor == null && !myRunConfiguration.getInterpreterPath().isEmpty()) {
      eelDescriptor = nullizeIfLocal(getEelDescriptor(Path.of(myRunConfiguration.getInterpreterPath())));
    }

    if (eelDescriptor == null) {
      return getEelDescriptor(myProject);
    }
    else {
      return eelDescriptor;
    }
  }

  private static @Nullable EelDescriptor nullizeIfLocal(@NotNull EelDescriptor eelDescriptor) {
    if (eelDescriptor == LocalEelDescriptor.INSTANCE) {
      return null;
    }
    else {
      return eelDescriptor;
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
                                              @NotNull EelDescriptor eelDescriptor) {
    systemDependentPath = convertPathUsingEel(systemDependentPath, eelDescriptor);

    if (!(eelDescriptor.getPlatform() instanceof EelPlatform.Windows)) return ShStringUtil.quote(systemDependentPath);
    String escapedPath = StringUtil.escapeQuotes(systemDependentPath);
    return StringUtil.containsWhitespaces(systemDependentPath) ? StringUtil.QUOTER.apply(escapedPath) : escapedPath;
  }

  private static String convertPathUsingEel(@NotNull String path, @NotNull EelDescriptor eelDescriptor) {
    if (path.isEmpty()) return path;
    if (eelDescriptor == LocalEelDescriptor.INSTANCE) return path;
    return EelNioBridgeServiceKt.asEelPath(Path.of(path)).toString();
  }
}