// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.runner;

import com.intellij.execution.configuration.EnvironmentVariablesData;
import com.intellij.execution.wsl.WslPath;
import com.intellij.ide.impl.TrustedProjects;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.wsl.WslConstants;
import com.intellij.terminal.ui.TerminalWidget;
import com.intellij.util.EnvironmentRestorer;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.CollectionFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.plugins.terminal.ShellStartupOptions;
import org.jetbrains.plugins.terminal.TerminalProjectOptionsProvider;
import org.jetbrains.plugins.terminal.util.TerminalEnvironment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.jetbrains.plugins.terminal.LocalTerminalDirectRunner.isDirectory;
import static org.jetbrains.plugins.terminal.runner.LocalTerminalStartCommandBuilder.convertShellPathToCommand;

@ApiStatus.Internal
public class LocalOptionsConfigurer {
  private static final Logger LOG = Logger.getInstance(LocalOptionsConfigurer.class);

  private final Project myProject;

  public LocalOptionsConfigurer(Project project) { myProject = project; }

  public @NotNull ShellStartupOptions configureStartupOptions(@NotNull ShellStartupOptions baseOptions) {
    return configureStartupOptions1(baseOptions);
  }

  private @NotNull ShellStartupOptions configureStartupOptions1(@NotNull ShellStartupOptions baseOptions) {
    String workingDir = getWorkingDirectory(baseOptions.getWorkingDirectory(), myProject);
    Map<String, String> envs = getTerminalEnvironment(baseOptions.getEnvVariables(), workingDir);

    List<String> initialCommand = doGetInitialCommand(baseOptions);
    TerminalWidget widget = baseOptions.getWidget();
    if (widget != null) {
      widget.setShellCommand(initialCommand);
    }

    return baseOptions.builder()
      .shellCommand(initialCommand)
      .workingDirectory(workingDir)
      .envVariables(envs)
      .build();
  }

  @VisibleForTesting
  static @NotNull String getWorkingDirectory(@Nullable String directory, Project project) {
    if (directory != null && isDirectory(directory)) {
      return directory;
    }
    String configuredWorkingDirectory = TerminalProjectOptionsProvider.getInstance(project).getStartingDirectory();
    if (configuredWorkingDirectory != null && isDirectory(configuredWorkingDirectory)) {
      return configuredWorkingDirectory;
    }
    String defaultWorkingDirectory = TerminalProjectOptionsProvider.getInstance(project).getDefaultStartingDirectory();
    if (defaultWorkingDirectory != null && isDirectory(defaultWorkingDirectory)) {
      return defaultWorkingDirectory;
    }
    VirtualFile projectDir = ProjectUtil.guessProjectDir(project);
    if (projectDir != null) {
      return VfsUtilCore.virtualToIoFile(projectDir).getAbsolutePath();
    }
    return SystemProperties.getUserHome();
  }

  private @NotNull Map<String, String> getTerminalEnvironment(@NotNull Map<String, String> baseEnvs, @NotNull String workingDir) {
    Map<String, String> envs = SystemInfo.isWindows ? CollectionFactory.createCaseInsensitiveStringMap() : new HashMap<>();
    EnvironmentVariablesData envData = TerminalProjectOptionsProvider.getInstance(myProject).getEnvData();
    if (envData.isPassParentEnvs()) {
      envs.putAll(System.getenv());
      EnvironmentRestorer.restoreOverriddenVars(envs);
    }
    else {
      LOG.info("No parent environment passed");
    }

    envs.putAll(baseEnvs);
    if (!SystemInfo.isWindows) {
      envs.put("TERM", "xterm-256color");
    }
    envs.put("TERMINAL_EMULATOR", "JetBrains-JediTerm");
    envs.put("TERM_SESSION_ID", UUID.randomUUID().toString());

    TerminalEnvironment.INSTANCE.setCharacterEncoding(envs);

    if (TrustedProjects.isTrusted(myProject)) {
      PathMacroManager macroManager = PathMacroManager.getInstance(myProject);
      for (Map.Entry<String, String> env : envData.getEnvs().entrySet()) {
        envs.put(env.getKey(), macroManager.expandPath(env.getValue()));
      }
      if (WslPath.isWslUncPath(workingDir)) {
        setupWslEnv(envData.getEnvs(), envs);
      }
    }
    return envs;
  }

  private @NotNull List<String> doGetInitialCommand(@NotNull ShellStartupOptions options) {
    return getInitialCommandInternal(options);
  }

  private @NotNull List<String> getInitialCommandInternal(ShellStartupOptions startupOptions) {
    List<String> shellCommand = startupOptions != null ? startupOptions.getShellCommand() : null;
    return shellCommand != null ? shellCommand : convertShellPathToCommand(getShellPath());
  }

  private @NotNull String getShellPath() {
    return TerminalProjectOptionsProvider.getInstance(myProject).getShellPath();
  }

  private static void setupWslEnv(@NotNull Map<String, String> userEnvs, @NotNull Map<String, String> resultEnvs) {
    String wslEnv = userEnvs.keySet().stream().map(name -> name + "/u").collect(Collectors.joining(":"));
    if (wslEnv.isEmpty()) return;
    String prevValue = userEnvs.get(WslConstants.WSLENV);
    if (prevValue == null) {
      prevValue = System.getenv(WslConstants.WSLENV);
    }
    String newWslEnv = prevValue != null ? StringUtil.trimEnd(prevValue, ':') + ':' + wslEnv : wslEnv;
    resultEnvs.put(WslConstants.WSLENV, newWslEnv);
  }
}
