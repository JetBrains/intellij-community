// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.platform.eel.EelDescriptor;
import com.intellij.platform.eel.path.EelPath;
import com.intellij.platform.eel.provider.LocalEelDescriptor;
import com.intellij.platform.ide.productMode.IdeProductMode;
import com.intellij.terminal.pty.PtyProcessTtyConnector;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.execution.ParametersListUtil;
import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.TtyConnector;
import com.pty4j.PtyProcess;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.fus.ReworkedTerminalUsageCollector;
import org.jetbrains.plugins.terminal.fus.TerminalUsageTriggerCollector;
import org.jetbrains.plugins.terminal.runner.LocalOptionsConfigurer;
import org.jetbrains.plugins.terminal.runner.LocalShellIntegrationInjector;
import org.jetbrains.plugins.terminal.runner.LocalTerminalStartCommandBuilder;
import org.jetbrains.plugins.terminal.shell_integration.TerminalPSReadLineUpdateUtil;
import org.jetbrains.plugins.terminal.startup.TerminalExecOptionsCustomizationKt;
import org.jetbrains.plugins.terminal.startup.TerminalProcessType;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.jetbrains.plugins.terminal.TerminalStartupKt.shouldUseEelApi;
import static org.jetbrains.plugins.terminal.TerminalStartupKt.startLocalProcess;
import static org.jetbrains.plugins.terminal.TerminalStartupKt.startProcess;
import static org.jetbrains.plugins.terminal.util.TerminalUtilKt.toExistentNioDirectory;

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

    if (IdeProductMode.isFrontend() && updatedOptions.getEelDescriptorNotNull() == LocalEelDescriptor.INSTANCE) {
      throw new IllegalStateException(("""
                                         It is prohibited to start a local process in RemDev mode. Something went wrong.
                                         Requested options: %s
                                         Configured options: %s
                                         """).formatted(baseOptions, updatedOptions));
    }

    if (updatedOptions.getProcessType() == TerminalProcessType.SHELL && enableShellIntegration()) {
      updatedOptions = LocalShellIntegrationInjector.injectShellIntegration(updatedOptions,
                                                                            isGenOneTerminalEnabled(),
                                                                            isGenTwoTerminalEnabled());
    }
    if (updatedOptions.getProcessType() == TerminalProcessType.SHELL) {
      updatedOptions = TerminalPSReadLineUpdateUtil.configureOptions(updatedOptions);
    }
    return TerminalExecOptionsCustomizationKt.applyExecOptionsCustomizers(myProject, updatedOptions);
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
    EelDescriptor eelDescriptor = options.getEelDescriptorNotNull();
    List<String> command = Objects.requireNonNull(options.getShellCommand(), () -> {
      return "Shell command must not be null, " + options;
    });
    Map<String, String> envs = options.getEnvVariables();
    TermSize initialTermSize = Objects.requireNonNull(options.getInitialTermSize(), () -> {
      return "Initial term size must not be null, " + options;
    });
    String workingDir = Objects.requireNonNull(options.getWorkingDirectory(), () -> {
      return "Working directory must not be null, " + options;
    });
    EelPath workingDirectoryEelPath = options.getWorkingDirectoryEelPathNotNull();

    var shellIntegration = options.getShellIntegration();
    boolean isBlockTerminal =
      (isGenOneTerminalEnabled() && shellIntegration != null && shellIntegration.getCommandBlocks());

    var commandLine = ParametersListUtil.join(command);
    if (isGenTwoTerminalEnabled()) {
      ReworkedTerminalUsageCollector.logLocalShellStarted(myProject, commandLine);
    }
    else {
      TerminalUsageTriggerCollector.triggerLocalShellStarted(myProject, commandLine, isBlockTerminal);
    }

    try {
      long startNano = System.nanoTime();
      ShellProcessHolder processHolder;
      if (shouldUseEelApi()) {
        processHolder = startProcess(eelDescriptor, command, envs, workingDirectoryEelPath, initialTermSize);
      }
      else {
        processHolder = startLocalProcess(command, envs, workingDir, initialTermSize);
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

  private static @NotNull String stringifyProcessInfo(@NotNull List<String> command,
                                                      @NotNull String workingDirectory,
                                                      @Nullable TermSize initialTermSize,
                                                      @NotNull Map<String, String> environment,
                                                      boolean envDiff) {
    String info = command + " in " + workingDirectory + (isDirectory(workingDirectory) ? "" : " [no such directory]") +
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

  private static boolean isDirectory(@NotNull String directory) {
    return toExistentNioDirectory(directory, null) != null;
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
}
