// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal;

import com.intellij.execution.CommandLineUtil;
import com.intellij.execution.configuration.EnvironmentVariablesData;
import com.intellij.execution.process.LocalPtyOptions;
import com.intellij.execution.process.ProcessService;
import com.intellij.execution.wsl.WslPath;
import com.intellij.ide.impl.TrustedProjects;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.wsl.WslConstants;
import com.intellij.terminal.pty.PtyProcessTtyConnector;
import com.intellij.terminal.ui.TerminalWidget;
import com.intellij.util.*;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.execution.ParametersListUtil;
import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.TtyConnector;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.unix.UnixPtyProcess;
import kotlin.jvm.functions.Function0;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.block.TerminalUsageLocalStorage;
import org.jetbrains.plugins.terminal.fus.TerminalUsageTriggerCollector;
import org.jetbrains.plugins.terminal.shell_integration.CommandBlockIntegration;
import org.jetbrains.plugins.terminal.util.ShellIntegration;
import org.jetbrains.plugins.terminal.util.ShellType;
import org.jetbrains.plugins.terminal.util.TerminalEnvironment;

import java.io.File;
import java.io.IOException;
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

public class LocalTerminalDirectRunner extends AbstractTerminalRunner<PtyProcess> {
  private static final Logger LOG = Logger.getInstance(LocalTerminalDirectRunner.class);
  private static final String JEDITERM_USER_RCFILE = "JEDITERM_USER_RCFILE";
  private static final String ZDOTDIR = "ZDOTDIR";
  private static final String IJ_ZSH_DIR = "JETBRAINS_INTELLIJ_ZSH_DIR";
  private static final String IJ_COMMAND_END_MARKER = "JETBRAINS_INTELLIJ_COMMAND_END_MARKER";
  private static final String IJ_COMMAND_HISTORY_FILE_ENV = "__INTELLIJ_COMMAND_HISTFILE__";
  private static final String LOGIN_SHELL = "LOGIN_SHELL";
  @ApiStatus.Internal
  public static final String LOGIN_CLI_OPTION = "--login";
  @ApiStatus.Internal
  public static final List<String> LOGIN_CLI_OPTIONS = List.of(LOGIN_CLI_OPTION, "-l");
  private static final String INTERACTIVE_CLI_OPTION = "-i";
  private static final String BASH_NAME = "bash";
  private static final String SH_NAME = "sh";
  private static final String ZSH_NAME = "zsh";
  private static final String FISH_NAME = "fish";

  protected final Charset myDefaultCharset;
  private final ThreadLocal<ShellStartupOptions> myStartupOptionsThreadLocal = new ThreadLocal<>();

  public LocalTerminalDirectRunner(Project project) {
    super(project);
    myDefaultCharset = StandardCharsets.UTF_8;
  }

  @Nullable
  private static String findRCFile(@NotNull String shellName) {
    String rcfile = switch (shellName) {
      case BASH_NAME, SH_NAME -> "shell-integrations/bash/bash-integration.bash";
      case ZSH_NAME -> "shell-integrations/zsh/.zshenv";
      case FISH_NAME -> "shell-integrations/fish/fish-integration.fish";
      default -> null;
    };
    if (rcfile == null && isPowerShell(shellName)) {
      rcfile = "shell-integrations/powershell/powershell-integration.ps1";
    }
    if (rcfile != null) {
      try {
        return findAbsolutePath(rcfile);
      }
      catch (Exception e) {
        LOG.warn("Unable to find " + rcfile + " configuration file", e);
      }
    }
    return null;
  }

  private static boolean isPowerShell(@NotNull String shellName) {
    return shellName.equalsIgnoreCase("powershell") ||
           shellName.equalsIgnoreCase("powershell.exe") ||
           shellName.equalsIgnoreCase("pwsh") ||
           shellName.equalsIgnoreCase("pwsh.exe");
  }

  @NotNull
  private static String findAbsolutePath(@NotNull String relativePath) throws IOException {
    String jarPath = PathUtil.getJarPathForClass(LocalTerminalDirectRunner.class);
    final File result;
    if (jarPath.endsWith(".jar")) {
      File jarFile = new File(jarPath);
      if (!jarFile.isFile()) {
        throw new IOException("Broken installation: " + jarPath + " is not a file");
      }
      File pluginBaseDir = jarFile.getParentFile().getParentFile();
      result = new File(pluginBaseDir, relativePath);
    }
    else {
      Application application = ApplicationManager.getApplication();
      if (application != null && application.isInternal()) {
        jarPath = StringUtil.trimEnd(jarPath.replace('\\', '/'), '/') + '/';
        String srcDir = jarPath.replace("/out/classes/production/intellij.terminal/",
                                        "/community/plugins/terminal/resources/");
        if (new File(srcDir).isDirectory()) {
          jarPath = srcDir;
        }
      }
      result = new File(jarPath, relativePath);
    }
    if (!result.isFile()) {
      throw new IOException("Cannot find " + relativePath + ": " + result.getAbsolutePath() + " is not a file");
    }
    return result.getAbsolutePath();
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
      updatedOptions = injectShellIntegration(updatedOptions);
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

  private static @Nullable ShellTerminalWidget getShellTerminalWidget(@Nullable ShellStartupOptions options) {
    TerminalWidget widget = options != null ? options.getWidget() : null;
    return widget != null ? ShellTerminalWidget.asShellJediTermWidget(widget) : null;
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
    return injectShellIntegration(options);
  }

  // todo: it would be great to extract block terminal configuration from here
  private @NotNull ShellStartupOptions injectShellIntegration(@NotNull ShellStartupOptions options) {
    List<String> shellCommand = options.getShellCommand();
    String shellExe = ContainerUtil.getFirstItem(shellCommand);
    if (shellCommand == null || shellExe == null) return options;

    List<String> arguments = new ArrayList<>(shellCommand.subList(1, shellCommand.size()));
    Map<String, String> envs = ShellStartupOptionsKt.createEnvVariablesMap(options.getEnvVariables());
    ShellIntegration integration = null;

    List<String> resultCommand = new ArrayList<>();
    resultCommand.add(shellExe);

    String shellName = PathUtil.getFileName(shellExe);
    String rcFilePath = findRCFile(shellName);
    if (rcFilePath != null) {
      boolean isBlockTerminal = isBlockTerminalSupported(shellName);
      if (shellName.equals(BASH_NAME) || (SystemInfo.isMac && shellName.equals(SH_NAME))) {
        addRcFileArgument(envs, arguments, resultCommand, rcFilePath, "--rcfile");
        // remove --login to enable --rcfile sourcing
        boolean loginShell = arguments.removeAll(LOGIN_CLI_OPTIONS);
        setLoginShellEnv(envs, loginShell);
        setCommandHistoryFile(options, envs);
        integration = new ShellIntegration(ShellType.BASH, isBlockTerminal ? new CommandBlockIntegration() : null);
      }
      else if (shellName.equals(ZSH_NAME)) {
        String zdotdir = envs.get(ZDOTDIR);
        if (StringUtil.isNotEmpty(zdotdir)) {
          envs.put("_INTELLIJ_ORIGINAL_ZDOTDIR", zdotdir);
        }
        String zshDir = PathUtil.getParentPath(rcFilePath);
        envs.put(ZDOTDIR, zshDir);
        envs.put(IJ_ZSH_DIR, zshDir);
        integration = new ShellIntegration(ShellType.ZSH, isBlockTerminal ? new CommandBlockIntegration() : null);
      }
      else if (shellName.equals(FISH_NAME)) {
        // `--init-command=COMMANDS` is available since Fish 2.7.0 (released November 23, 2017)
        // Multiple `--init-command=COMMANDS` are supported.
        resultCommand.add("--init-command=source " + CommandLineUtil.posixQuote(rcFilePath));
        integration = new ShellIntegration(ShellType.FISH, isBlockTerminal ? new CommandBlockIntegration() : null);
      }
      else if (isPowerShell(shellName)) {
        resultCommand.addAll(arguments);
        arguments.clear();
        resultCommand.addAll(List.of("-NoExit", "-ExecutionPolicy", "Bypass", "-File", rcFilePath));
        integration = new ShellIntegration(ShellType.POWERSHELL, isBlockTerminal ? new CommandBlockIntegration(true) : null);
      }
    }

    if (isBlockTerminalEnabled() && integration != null && integration.getCommandBlockIntegration() != null) {
      envs.put("INTELLIJ_TERMINAL_COMMAND_BLOCKS", "1");
      // Pretend to be Fig.io terminal to avoid it breaking IntelliJ shell integration:
      // at startup it runs a sub-shell without IntelliJ shell integration
      envs.put("FIG_TERM", "1");
      // CodeWhisperer runs a nested shell unavailable for injecting IntelliJ shell integration.
      // Zsh and Bash are affected although these shell integrations are installed differently.
      // We need to either change how IntelliJ injects shell integrations to support nested shells
      // or disable running a nested shell by CodeWhisperer. Let's do the latter:
      envs.put("PROCESS_LAUNCHED_BY_CW", "1");
      // The same story as the above. Amazon Q is a renamed CodeWhisperer. So, they also renamed the env variables.
      envs.put("PROCESS_LAUNCHED_BY_Q", "1");
    }

    CommandBlockIntegration commandIntegration = integration != null ? integration.getCommandBlockIntegration() : null;
    String commandEndMarker = commandIntegration != null ? commandIntegration.getCommandEndMarker() : null;
    if (commandEndMarker != null) {
      envs.put(IJ_COMMAND_END_MARKER, commandEndMarker);
    }

    resultCommand.addAll(arguments);
    return options.builder()
      .shellCommand(resultCommand)
      .envVariables(envs)
      .shellIntegration(integration)
      .build();
  }

  private static void setCommandHistoryFile(@NotNull ShellStartupOptions startupOptions, @NotNull Map<String, String> envs) {
    Function0<Path> commandHistoryFileProvider = startupOptions.getCommandHistoryFileProvider();
    Path commandHistoryFile = commandHistoryFileProvider != null ? commandHistoryFileProvider.invoke() : null;
    if (commandHistoryFile != null) {
      envs.put(IJ_COMMAND_HISTORY_FILE_ENV, commandHistoryFile.toString());
      ShellTerminalWidget widget = getShellTerminalWidget(startupOptions);
      if (widget != null) {
        widget.setCommandHistoryFilePath(commandHistoryFile.toString());
      }
    }
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

  private static boolean isBashZshFish(@NotNull String shellName) {
    return shellName.equals(BASH_NAME) || (SystemInfo.isMac && shellName.equals(SH_NAME)) ||
           shellName.equals(ZSH_NAME) || shellName.equals(FISH_NAME);
  }

  private static void setLoginShellEnv(@NotNull Map<String, String> envs, boolean loginShell) {
    if (loginShell) {
      envs.put(LOGIN_SHELL, "1");
    }
  }

  private static void addRcFileArgument(Map<String, String> envs,
                                        List<String> arguments,
                                        List<String> result,
                                        String rcFilePath, String rcfileOption) {
    result.add(rcfileOption);
    result.add(rcFilePath);
    int idx = arguments.indexOf(rcfileOption);
    if (idx >= 0) {
      arguments.remove(idx);
      if (idx < arguments.size()) {
        String userRcFile = FileUtil.expandUserHome(arguments.get(idx));
        // do not set the same RC file path to avoid sourcing recursion
        if (!userRcFile.equals(rcFilePath)) {
          envs.put(JEDITERM_USER_RCFILE, FileUtil.expandUserHome(arguments.get(idx)));
        }
        arguments.remove(idx);
      }
    }
  }

}
