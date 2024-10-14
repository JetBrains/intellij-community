// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal;

import com.intellij.execution.configuration.EnvironmentVariablesData;
import com.intellij.execution.process.LocalPtyOptions;
import com.intellij.execution.process.ProcessService;
import com.intellij.execution.wsl.WslPath;
import com.intellij.ide.impl.TrustedProjects;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.wsl.WslConstants;
import com.intellij.terminal.pty.PtyProcessTtyConnector;
import com.intellij.terminal.ui.TerminalWidget;
import com.intellij.util.ArrayUtil;
import com.intellij.util.EnvironmentRestorer;
import com.intellij.util.SystemProperties;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.CollectionFactory;
import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.TtyConnector;
import com.pty4j.PtyProcess;
import com.pty4j.unix.UnixPtyProcess;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.block.TerminalUsageLocalStorage;
import org.jetbrains.plugins.terminal.fus.TerminalUsageTriggerCollector;
import org.jetbrains.plugins.terminal.runner.LocalShellIntegrationInjector;
import org.jetbrains.plugins.terminal.util.TerminalEnvironment;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.jetbrains.plugins.terminal.LocalBlockTerminalRunner.*;
import static org.jetbrains.plugins.terminal.runner.LocalTerminalStartCommandBuilder.convertShellPathToCommand;
import static org.jetbrains.plugins.terminal.util.ShellNameUtil.*;

public class LocalTerminalDirectRunner extends AbstractTerminalRunner<PtyProcess> {
  private static final Logger LOG = Logger.getInstance(LocalTerminalDirectRunner.class);
  @ApiStatus.Internal
  public static final String LOGIN_CLI_OPTION = "--login";
  @ApiStatus.Internal
  public static final List<String> LOGIN_CLI_OPTIONS = List.of(LOGIN_CLI_OPTION, "-l");

  protected final Charset myDefaultCharset;
  private final ThreadLocal<ShellStartupOptions> myStartupOptionsThreadLocal = new ThreadLocal<>();
  private final LocalShellIntegrationInjector shellIntegrationInjector;

  public LocalTerminalDirectRunner(Project project) {
    super(project);
    myDefaultCharset = StandardCharsets.UTF_8;
    shellIntegrationInjector = new LocalShellIntegrationInjector(() -> isBlockTerminalEnabled());
  }

  @NotNull
  public static LocalTerminalDirectRunner createTerminalRunner(Project project) {
    return new LocalTerminalDirectRunner(project);
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

  @Override
  public @NotNull ShellStartupOptions configureStartupOptions(@NotNull ShellStartupOptions baseOptions) {
    String workingDir = getWorkingDirectory(baseOptions.getWorkingDirectory());
    Map<String, String> envs = getTerminalEnvironment(baseOptions.getEnvVariables(), workingDir);

    List<String> initialCommand = doGetInitialCommand(baseOptions, envs);
    TerminalWidget widget = baseOptions.getWidget();
    if (widget != null) {
      widget.setShellCommand(initialCommand);
    }

    ShellStartupOptions updatedOptions = baseOptions.builder()
      .shellCommand(initialCommand)
      .workingDirectory(workingDir)
      .envVariables(envs)
      .build();
    if (enableShellIntegration()) {
      updatedOptions = shellIntegrationInjector.configureStartupOptions(updatedOptions);
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
    boolean isBlockTerminal = isBlockTerminalEnabled() && shellIntegration != null && shellIntegration.getCommandBlockIntegration() != null;
    TerminalUsageTriggerCollector.triggerLocalShellStarted(myProject, command, isBlockTerminal);

    if (isBlockTerminal) {
      TerminalUsageLocalStorage.getInstance().recordBlockTerminalUsed();
    }

    try {
      long startNano = System.nanoTime();
      PtyProcess process = (PtyProcess)ProcessService.getInstance().startPtyProcess(
        command,
        workingDir,
        envs,
        LocalPtyOptions.defaults().builder()
          .initialColumns(initialTermSize != null ? initialTermSize.getColumns() : -1)
          .initialRows(initialTermSize != null ? initialTermSize.getRows() : -1)
          .useWinConPty(LocalPtyOptions.shouldUseWinConPty())
          .build(),
        null,
        false,
        false,
        false
      );
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

  private @NotNull List<String> doGetInitialCommand(@NotNull ShellStartupOptions options, @NotNull Map<String, String> envs) {
    try {
      myStartupOptionsThreadLocal.set(options);
      return getInitialCommand(envs);
    }
    finally {
      myStartupOptionsThreadLocal.remove();
    }
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

  private @NotNull String getWorkingDirectory(@Nullable String directory) {
    if (directory != null && isDirectory(directory)) {
      return directory;
    }
    String configuredWorkingDirectory = TerminalProjectOptionsProvider.getInstance(myProject).getStartingDirectory();
    if (configuredWorkingDirectory != null && isDirectory(configuredWorkingDirectory)) {
      return configuredWorkingDirectory;
    }
    String defaultWorkingDirectory = TerminalProjectOptionsProvider.getInstance(myProject).getDefaultStartingDirectory();
    if (defaultWorkingDirectory != null && isDirectory(defaultWorkingDirectory)) {
      return defaultWorkingDirectory;
    }
    VirtualFile projectDir = ProjectUtil.guessProjectDir(myProject);
    if (projectDir != null) {
      return VfsUtilCore.virtualToIoFile(projectDir).getAbsolutePath();
    }
    return SystemProperties.getUserHome();
  }

  private static boolean isDirectory(@NotNull String directory) {
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
    return new PtyProcessTtyConnector(process, myDefaultCharset) {

      @Override
      public void close() {
        if (process instanceof UnixPtyProcess) {
          ((UnixPtyProcess)process).hangup();
          AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> {
            if (process.isAlive()) {
              LOG.info("Terminal hasn't been terminated by SIGHUP, performing default termination");
              process.destroy();
            }
          }, 1000, TimeUnit.MILLISECONDS);
        }
        else {
          process.destroy();
        }
      }

      @Override
      public void resize(@NotNull TermSize termSize) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("resize to " + termSize);
        }
        super.resize(termSize);
      }
    };
  }

  @Override
  public @NotNull String getDefaultTabTitle() {
    return TerminalOptionsProvider.getInstance().getTabName();
  }

  /**
   * @param envs environment variables
   * @return initial command. The result command to execute is calculated by applying
   *         {@link LocalTerminalCustomizer#customizeCommandAndEnvironment} to it.
   */
  public @NotNull List<String> getInitialCommand(@NotNull Map<String, String> envs) {
    ShellStartupOptions startupOptions = myStartupOptionsThreadLocal.get();
    List<String> shellCommand = startupOptions != null ? startupOptions.getShellCommand() : null;
    return shellCommand != null ? shellCommand : convertShellPathToCommand(getShellPath());
  }

  @ApiStatus.Internal
  protected boolean isBlockTerminalEnabled() {
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
    List<String> command = convertShellPathToCommand(shellPath);
    if (shellIntegration) {
      ShellStartupOptions options = injectShellIntegration(command, envs);
      return Objects.requireNonNull(options.getShellCommand());
    }
    return command;
  }

  @NotNull ShellStartupOptions injectShellIntegration(@NotNull List<String> shellCommand,
                                                             @NotNull Map<String, String> envs) {
    ShellStartupOptions options = new ShellStartupOptions.Builder().shellCommand(shellCommand).envVariables(envs).build();
    return shellIntegrationInjector.configureStartupOptions(options);
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
