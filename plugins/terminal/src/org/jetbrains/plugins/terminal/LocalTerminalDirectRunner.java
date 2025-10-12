// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.terminal.pty.PtyProcessTtyConnector;
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
import org.jetbrains.plugins.terminal.shell_integration.TerminalPSReadLineUpdateUtil;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.jetbrains.plugins.terminal.TerminalStartupKt.*;

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
    updatedOptions = TerminalPSReadLineUpdateUtil.configureOptions(updatedOptions);
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

  /**
   * @deprecated use {@link #createTtyConnector(ShellStartupOptions)} instead
   * Kept due to external usages.
   */
  @SuppressWarnings("removal")
  @Deprecated(forRemoval = true)
  @Override
  public @NotNull PtyProcess createProcess(@NotNull ShellStartupOptions options) throws ExecutionException {
    return doCreateProcess(options).getPtyProcess();
  }

  private @NotNull ShellProcessHolder doCreateProcess(@NotNull ShellStartupOptions options) throws ExecutionException {
    String[] command = ArrayUtil.toStringArray(options.getShellCommand());
    Map<String, String> envs = options.getEnvVariables();
    TermSize initialTermSize = options.getInitialTermSize();
    String workingDir = options.getWorkingDirectory();
    if (workingDir == null) {
      throw new IllegalStateException("Working directory must not be null, startup options: " + options);
    }

    var shellIntegration = options.getShellIntegration();
    boolean isBlockTerminal =
      (isGenOneTerminalEnabled() && shellIntegration != null && shellIntegration.getCommandBlocks());

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
      ShellProcessHolder processHolder;
      if (workingDirPath != null && shouldUseEelApi()) {
        processHolder = startProcess(
          List.of(command), envs, workingDirPath, Objects.requireNonNull(initialTermSize)
        );
      }
      else {
        processHolder = startLocalProcess(
          List.of(command), envs, workingDir, Objects.requireNonNull(initialTermSize)
        );
      }
      LOG.info("Started " + processHolder.getPtyProcess().getClass().getName() + " in " + TimeoutUtil.getDurationMillis(startNano)
               + " ms from " + stringifyProcessInfo(command, workingDir, initialTermSize, envs, !LOG.isDebugEnabled()));
      return processHolder;
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
  public @NotNull TtyConnector createTtyConnector(@NotNull ShellStartupOptions startupOptions) throws ExecutionException {
    ShellProcessHolder processHolder = doCreateProcess(startupOptions);
    return new LocalTerminalTtyConnector(processHolder, myDefaultCharset);
  }

  /**
   * @deprecated use {@link #createTtyConnector(ShellStartupOptions)} instead
   * Kept due to external usages.
   */
  @SuppressWarnings("removal")
  @Deprecated(forRemoval = true)
  @Override
  public @NotNull TtyConnector createTtyConnector(@NotNull PtyProcess process) {
    return new PtyProcessTtyConnector(process, myDefaultCharset);
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

  @ApiStatus.Internal
  protected boolean isGenTwoTerminalEnabled() {
    return false;
  }

  private @NotNull String getShellPath() {
    return TerminalProjectOptionsProvider.getInstance(myProject).getShellPath();
  }

  @ApiStatus.Internal
  @VisibleForTesting
  public @NotNull ShellStartupOptions injectShellIntegration(@NotNull List<String> shellCommand,
                                                             @NotNull Map<String, String> envs) {
    ShellStartupOptions options = new ShellStartupOptions.Builder().shellCommand(shellCommand).envVariables(envs).build();
    return LocalShellIntegrationInjector.injectShellIntegration(options, isGenOneTerminalEnabled(), isGenTwoTerminalEnabled());
  }
}
