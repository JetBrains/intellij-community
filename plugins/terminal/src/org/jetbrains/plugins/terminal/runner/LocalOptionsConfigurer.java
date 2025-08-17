// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.runner;

import com.intellij.execution.configuration.EnvironmentVariablesData;
import com.intellij.execution.wsl.WslPath;
import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.eel.EelDescriptor;
import com.intellij.platform.eel.EelPlatformKt;
import com.intellij.platform.eel.provider.EelProviderUtil;
import com.intellij.platform.eel.provider.LocalEelDescriptor;
import com.intellij.platform.eel.provider.utils.EelUtilsKt;
import com.intellij.terminal.ui.TerminalWidget;
import com.intellij.util.EnvironmentRestorer;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.system.OS;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.plugins.terminal.ShellStartupOptions;
import org.jetbrains.plugins.terminal.TerminalProjectOptionsProvider;
import org.jetbrains.plugins.terminal.TerminalStartupKt;
import org.jetbrains.plugins.terminal.util.TerminalEnvironment;
import org.jetbrains.plugins.terminal.util.WslEnvInterop;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.jetbrains.plugins.terminal.LocalTerminalDirectRunner.isDirectory;
import static org.jetbrains.plugins.terminal.TerminalStartupKt.findEelDescriptor;

@ApiStatus.Internal
public final class LocalOptionsConfigurer {
  private static final Logger LOG = Logger.getInstance(LocalOptionsConfigurer.class);
  private static final String TERMINAL_EMULATOR = "TERMINAL_EMULATOR";
  private static final String TERM_SESSION_ID = "TERM_SESSION_ID";

  public static @NotNull ShellStartupOptions configureStartupOptions(@NotNull ShellStartupOptions baseOptions, @NotNull Project project) {
    String workingDir = getWorkingDirectory(baseOptions.getWorkingDirectory(), project);
    List<String> initialCommand = getInitialCommand(baseOptions, project, workingDir);
    var eelDescriptor = findEelDescriptor(workingDir, initialCommand);
    Map<String, String> envs = getTerminalEnvironment(baseOptions.getEnvVariables(), project, eelDescriptor, initialCommand);

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
    String validDirectory = findValidWorkingDirectory(directory);
    if (validDirectory != null) {
      return validDirectory;
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

  /**
   * @param path can be null, incorrect path or path to the valid file or directory.
   * @return the provided path if it is a valid directory path or parent directory path if provided path points to a valid file.
   */
  private static @Nullable String findValidWorkingDirectory(@Nullable String path) {
    if (path == null) return null;

    Path directoryPath;
    try {
      directoryPath = Path.of(path);
    }
    catch (InvalidPathException e) {
      return null;
    }

    if (Files.isDirectory(directoryPath)) {
      return directoryPath.toString();
    }

    Path parentPath = directoryPath.getParent();
    if (parentPath != null && Files.isDirectory(parentPath)) {
      return parentPath.toString();
    }

    return null;
  }

  private static @NotNull Map<String, String> getTerminalEnvironment(@NotNull Map<String, String> baseEnvs,
                                                                     @NotNull Project project,
                                                                     @NotNull EelDescriptor eelDescriptor,
                                                                     @NotNull List<String> shellCommand) {
    final var isWindows = EelPlatformKt.isWindows(eelDescriptor.getOsFamily());

    Map<String, String> envs = isWindows ? CollectionFactory.createCaseInsensitiveStringMap() : new HashMap<>();
    EnvironmentVariablesData envData = TerminalProjectOptionsProvider.getInstance(project).getEnvData();
    if (envData.isPassParentEnvs()) {
      if (eelDescriptor == LocalEelDescriptor.INSTANCE) {
        // Use the default environment variables when running locally.
        // Calling `fetchLoginShellEnvVariables(eelDescriptor)` retrieves shell environment variables
        // via `com.intellij.util.EnvironmentUtil.getEnvironmentMap`, which can break PATH.
        envs.putAll(System.getenv());
      }
      else {
        envs.putAll(fetchLoginShellEnvVariables(eelDescriptor));
      }
      EnvironmentRestorer.restoreOverriddenVars(envs);
    }
    else {
      LOG.info("No parent environment passed");
    }

    envs.putAll(baseEnvs);
    if (!isWindows) {
      envs.put("TERM", "xterm-256color");
    }
    envs.put(TERMINAL_EMULATOR, "JetBrains-JediTerm");
    envs.put(TERM_SESSION_ID, UUID.randomUUID().toString());

    TerminalEnvironment.INSTANCE.setCharacterEncoding(envs);

    WslEnvInterop wslEnvInterop = new WslEnvInterop(eelDescriptor, shellCommand);
    if (TrustedProjects.isProjectTrusted(project)) {
      PathMacroManager macroManager = PathMacroManager.getInstance(project);
      for (Map.Entry<String, String> env : envData.getEnvs().entrySet()) {
        envs.put(env.getKey(), macroManager.expandPath(env.getValue()));
      }
      wslEnvInterop.passEnvsToWsl(envData.getEnvs().keySet());
    }
    wslEnvInterop.passEnvsToWsl(List.of(TERMINAL_EMULATOR, TERM_SESSION_ID));
    wslEnvInterop.applyTo(envs);
    return envs;
  }

  private static @NotNull List<String> getInitialCommand(
    @NotNull ShellStartupOptions options,
    @NotNull Project project,
    @NotNull String workingDir
  ) {
    List<String> shellCommand = fixShellCommand(options.getShellCommand());
    if (shellCommand != null) {
      return shellCommand;
    }
    String shellPath = fixShellPath(getShellPath(project), workingDir);
    return LocalTerminalStartCommandBuilder.convertShellPathToCommand(shellPath, workingDir);
  }

  private static @Nullable List<String> fixShellCommand(@Nullable List<String> shellCommand) {
    if (OS.CURRENT == OS.Windows && !TerminalStartupKt.shouldUseEelApi() &&
        isUnixPath(ContainerUtil.getFirstItem(shellCommand))) {
      return null; // use the default shell path
    }
    return shellCommand;
  }

  private static @NotNull String fixShellPath(@NotNull String shellPath, @NotNull String workingDirectory) {
    if (OS.CURRENT == OS.Windows && !TerminalStartupKt.shouldUseEelApi() && isUnixPath(shellPath)) {
      WslPath wslPath = WslPath.parseWindowsUncPath(workingDirectory);
      if (wslPath != null) {
        return "wsl.exe --distribution " + wslPath.getDistributionId();
      }
    }
    return shellPath;
  }

  private static boolean isUnixPath(@Nullable String path) {
    return path != null && path.startsWith("/") && !path.startsWith("//") /* UNC path */;
  }

  private static @NotNull String getShellPath(@NotNull Project project) {
    return TerminalProjectOptionsProvider.getInstance(project).getShellPath();
  }

  private static Map<String, String> fetchLoginShellEnvVariables(@NotNull EelDescriptor eelDescriptor) {
    return EelUtilsKt.fetchLoginShellEnvVariablesBlocking(EelProviderUtil.toEelApiBlocking(eelDescriptor).getExec());
  }
}
