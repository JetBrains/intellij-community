// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal;

import com.intellij.execution.process.LocalProcessService;
import com.intellij.execution.process.LocalPtyOptions;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ArrayUtil;
import com.intellij.util.TimeoutUtil;
import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.TtyConnector;
import com.pty4j.PtyProcess;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.plugins.terminal.fus.ReworkedTerminalUsageCollector;
import org.jetbrains.plugins.terminal.fus.TerminalUsageTriggerCollector;
import org.jetbrains.plugins.terminal.runner.LocalOptionsConfigurer;
import org.jetbrains.plugins.terminal.runner.LocalShellIntegrationInjector;
import org.jetbrains.plugins.terminal.runner.LocalTerminalStartCommandBuilder;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.jetbrains.plugins.terminal.LocalBlockTerminalRunner.*;
import static org.jetbrains.plugins.terminal.TerminalStartupKt.shouldUseEelApi;
import static org.jetbrains.plugins.terminal.TerminalStartupKt.startProcess;
import static org.jetbrains.plugins.terminal.util.ShellNameUtil.*;

public class LocalTerminalDirectRunner extends AbstractTerminalRunner<PtyProcess> {
  private static final Logger LOG = Logger.getInstance(LocalTerminalDirectRunner.class);
  @ApiStatus.Internal
  public static final String LOGIN_CLI_OPTION = "--login";
  @ApiStatus.Internal
  public static final List<String> LOGIN_CLI_OPTIONS = List.of(LOGIN_CLI_OPTION, "-l");

  protected final Charset myDefaultCharset;

  public LocalTerminalDirectRunner(Project project) {
    super(project);
    myDefaultCharset = StandardCharsets.UTF_8;
  }

  public static @NotNull LocalTerminalDirectRunner createTerminalRunner(Project project) {
    return new LocalTerminalDirectRunner(project);
  }

  @Override
  public @NotNull ShellStartupOptions configureStartupOptions(@NotNull ShellStartupOptions baseOptions) {
    ShellStartupOptions updatedOptions = LocalOptionsConfigurer.configureStartupOptions(baseOptions, myProject);
    if (enableShellIntegration()) {
      updatedOptions = LocalShellIntegrationInjector.injectShellIntegration(updatedOptions,
                                                                            isGenOneTerminalEnabled(),
                                                                            isGenTwoTerminalEnabled());
    }
    return applyTerminalCustomizers(updatedOptions);
  }

  private @NotNull ShellStartupOptions applyTerminalCustomizers(@NotNull ShellStartupOptions options) {
    String[] command = ArrayUtil.toStringArray(options.getShellCommand());
    Map<String, String> envs = ShellStartupOptionsKt.createEnvVariablesMap(options.getEnvVariables());
    for (LocalTerminalCustomizer customizer : LocalTerminalCustomizer.EP_NAME.getExtensions()) {
      try {
        command = customizer.customizeCommandAndEnvironment(myProject, options.getWorkingDirectory(), command, envs);
      }
      catch (Exception e) {
        LOG.error("Exception during customization of the terminal session", e);
      }
    }

    return options.builder()
      .shellCommand(Arrays.asList(command))
      .envVariables(envs)
      .build();
  }

  @Override
  public @NotNull PtyProcess createProcess(@NotNull ShellStartupOptions options) throws ExecutionException {
    String[] command = ArrayUtil.toStringArray(options.getShellCommand());
    Map<String, String> envs = options.getEnvVariables();
    TermSize initialTermSize = options.getInitialTermSize();
    String workingDir = options.getWorkingDirectory();
    if (workingDir == null) {
      throw new IllegalStateException("Working directory must not be null, startup options: " + options);
    }

    var shellIntegration = options.getShellIntegration();
    boolean isBlockTerminal =
      (isGenOneTerminalEnabled() && shellIntegration != null && shellIntegration.getCommandBlockIntegration() != null);

    if (isGenTwoTerminalEnabled()) {
      ReworkedTerminalUsageCollector.logLocalShellStarted(myProject, command);
    }
    else {
      TerminalUsageTriggerCollector.triggerLocalShellStarted(myProject, command, isBlockTerminal);
    }

    Path workingDirPath = null;
    try {
      workingDirPath = Path.of(workingDir);
    }
    catch (InvalidPathException ignored) {
    }
    try {
      long startNano = System.nanoTime();
      PtyProcess process;
      if (workingDirPath != null && shouldUseEelApi()) {
        process = startProcess(List.of(command), envs, workingDirPath, Objects.requireNonNull(initialTermSize));
      }
      else {
        process = (PtyProcess)LocalProcessService.getInstance().startPtyProcess(
          List.of(command),
          workingDir,
          envs,
          LocalPtyOptions.defaults().builder()
            .initialColumns(initialTermSize != null ? initialTermSize.getColumns() : -1)
            .initialRows(initialTermSize != null ? initialTermSize.getRows() : -1)
            .build(),
          false
        );
      }
      LOG.info("Started " + process.getClass().getName() + " in " + TimeoutUtil.getDurationMillis(startNano) + " ms from "
               + stringifyProcessInfo(command, workingDir, initialTermSize, envs, !LOG.isDebugEnabled()));
      return process;
    }
    catch (Exception e) {
      throw new ExecutionException("Failed to start " + stringifyProcessInfo(command, workingDir, initialTermSize, envs, false), e);
    }
  }

  protected boolean enableShellIntegration() {
    return TerminalOptionsProvider.getInstance().getShellIntegration();
  }

  private static @NotNull String stringifyProcessInfo(String @NotNull[] command,
                                                      @NotNull String workingDirectory,
                                                      @Nullable TermSize initialTermSize,
                                                      @NotNull Map<String, String> environment,
                                                      boolean envDiff) {
    String info = Arrays.toString(command) + " in " + workingDirectory + (isDirectory(workingDirectory) ? "" : " [no such directory]") +
                  ", [" + initialTermSize + "]";
    if (envDiff) {
      return info + ", diff_envs=" + getEnvironmentDiff(environment, System.getenv());
    }
    return info + ", envs=" + environment;
  }

  private static @NotNull Map<String, String> getEnvironmentDiff(@NotNull Map<String, String> environment,
                                                                 @NotNull Map<String, String> baseEnvironment) {
    return environment.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> {
        return Objects.equals(entry.getValue(), baseEnvironment.get(entry.getKey())) ? null : entry;
    }).filter(Objects::nonNull)
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (v1, v2) -> v1, LinkedHashMap::new));
  }

  @ApiStatus.Internal
  public static boolean isDirectory(@NotNull String directory) {
    try {
      boolean ok = Files.isDirectory(Path.of(directory));
      if (!ok) {
        LOG.info("Cannot start local terminal in " + directory + ": no such directory");
      }
      return ok;
    }
    catch (InvalidPathException e) {
      LOG.info("Cannot start local terminal in " + directory + ": invalid path", e);
      return false;
    }
  }

  @Override
  public @NotNull TtyConnector createTtyConnector(@NotNull PtyProcess process) {
    return new LocalTerminalTtyConnector(process, myDefaultCharset);
  }

  @Override
  public @NotNull String getDefaultTabTitle() {
    return TerminalOptionsProvider.getInstance().getTabName();
  }

  /**
   * @param envs environment variables
   * @return initial command. The result command to execute is calculated by applying
   *         {@link LocalTerminalCustomizer#customizeCommandAndEnvironment} to it.
   * @deprecated Use {@link LocalTerminalStartCommandBuilder#convertShellPathToCommand(String)}
   */
  @Deprecated(since = "2024.3", forRemoval = true)
  @SuppressWarnings("unused") // Has external usages
  public @NotNull List<String> getInitialCommand(@NotNull Map<String, String> envs) {
    return LocalTerminalStartCommandBuilder.convertShellPathToCommand(getShellPath());
  }

  @ApiStatus.Internal
  protected boolean isGenOneTerminalEnabled() {
    return false;
  }

  private @NotNull String getShellPath() {
    return TerminalProjectOptionsProvider.getInstance(myProject).getShellPath();
  }

  /** @deprecated to be removed */
  @ApiStatus.Internal
  @Deprecated(forRemoval = true)
  public @NotNull List<String> getCommand(@NotNull String shellPath,
                                                 @NotNull Map<String, String> envs,
                                                 boolean shellIntegration) {
    List<String> command = LocalTerminalStartCommandBuilder.convertShellPathToCommand(shellPath);
    if (shellIntegration) {
      ShellStartupOptions options = injectShellIntegration(command, envs);
      return Objects.requireNonNull(options.getShellCommand());
    }
    return command;
  }

  @ApiStatus.Internal
  @VisibleForTesting
  public @NotNull ShellStartupOptions injectShellIntegration(@NotNull List<String> shellCommand,
                                                             @NotNull Map<String, String> envs) {
    ShellStartupOptions options = new ShellStartupOptions.Builder().shellCommand(shellCommand).envVariables(envs).build();
    return LocalShellIntegrationInjector.injectShellIntegration(options, isGenOneTerminalEnabled(), isGenTwoTerminalEnabled());
  }

  /**
   * @return true if block terminal can be used with the provided shell name
   */
  @ApiStatus.Internal
  public static boolean isBlockTerminalSupported(@NotNull String shellName) {
    if (isPowerShell(shellName)) {
      return SystemInfo.isWin11OrNewer && Registry.is(BLOCK_TERMINAL_POWERSHELL_WIN11_REGISTRY, false) ||
             SystemInfo.isWin10OrNewer && !SystemInfo.isWin11OrNewer && Registry.is(BLOCK_TERMINAL_POWERSHELL_WIN10_REGISTRY, false) ||
             SystemInfo.isUnix && Registry.is(BLOCK_TERMINAL_POWERSHELL_UNIX_REGISTRY, false);
    }
    return shellName.equals(BASH_NAME)
           || SystemInfo.isMac && shellName.equals(SH_NAME)
           || shellName.equals(ZSH_NAME)
           || shellName.equals(FISH_NAME) && Registry.is(BLOCK_TERMINAL_FISH_REGISTRY, false);
  }
}
