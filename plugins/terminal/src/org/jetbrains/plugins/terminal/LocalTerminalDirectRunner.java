// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal;

import com.intellij.execution.CommandLineUtil;
import com.intellij.execution.TaskExecutor;
import com.intellij.execution.configuration.EnvironmentVariablesData;
import com.intellij.execution.process.*;
import com.intellij.execution.wsl.WslPath;
import com.intellij.ide.impl.TrustedProjects;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
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
import org.jetbrains.plugins.terminal.exp.TerminalWidgetImpl;
import org.jetbrains.plugins.terminal.util.TerminalEnvironment;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class LocalTerminalDirectRunner extends AbstractTerminalRunner<PtyProcess> {
  private static final Logger LOG = Logger.getInstance(LocalTerminalDirectRunner.class);
  private static final String JEDITERM_USER_RCFILE = "JEDITERM_USER_RCFILE";
  private static final String ZDOTDIR = "ZDOTDIR";
  private static final String IJ_COMMAND_HISTORY_FILE_ENV = "__INTELLIJ_COMMAND_HISTFILE__";
  private static final String LOGIN_SHELL = "LOGIN_SHELL";
  private static final String LOGIN_CLI_OPTION = "--login";
  private static final List<String> LOGIN_CLI_OPTIONS = List.of(LOGIN_CLI_OPTION, "-l");
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
      case BASH_NAME, SH_NAME -> "jediterm-bash.in";
      case ZSH_NAME -> "zsh/.zshenv";
      case FISH_NAME -> "fish/init.fish";
      default -> null;
    };
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

  private @NotNull Map<String, String> getTerminalEnvironment(@NotNull String workingDir) {
    Map<String, String> envs = SystemInfo.isWindows ? CollectionFactory.createCaseInsensitiveStringMap() : new HashMap<>();
    EnvironmentVariablesData envData = TerminalProjectOptionsProvider.getInstance(myProject).getEnvData();
    if (envData.isPassParentEnvs()) {
      envs.putAll(System.getenv());
      EnvironmentRestorer.restoreOverriddenVars(envs);
    }
    else {
      LOG.info("No parent environment passed");
    }

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

  @Override
  protected @NotNull TerminalWidget createShellTerminalWidget(@NotNull Disposable parent, @NotNull ShellStartupOptions startupOptions) {
    if (Registry.is("ide.experimental.ui.new.terminal", false)) {
      return new TerminalWidgetImpl(myProject, getSettingsProvider(), parent);
    }
    return super.createShellTerminalWidget(parent, startupOptions);
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
  public @NotNull PtyProcess createProcess(@NotNull ShellStartupOptions options) throws ExecutionException {
    String workingDir = getWorkingDirectory(options.getWorkingDirectory());
    Map<String, String> envs = getTerminalEnvironment(workingDir);

    List<String> initialCommand = doGetInitialCommand(options, envs);
    ShellTerminalWidget widget = getShellTerminalWidget(options);
    if (widget != null) {
      widget.setShellCommand(initialCommand);
    }
    if (enableShellIntegration()) {
      initialCommand = injectShellIntegration(initialCommand, envs, options);
    }
    String[] command = ArrayUtil.toStringArray(initialCommand);

    for (LocalTerminalCustomizer customizer : LocalTerminalCustomizer.EP_NAME.getExtensions()) {
      try {
        command = customizer.customizeCommandAndEnvironment(myProject, workingDir, command, envs);
      }
      catch (Exception e) {
        LOG.error("Exception during customization of the terminal session", e);
      }
    }

    TerminalUsageTriggerCollector.triggerLocalShellStarted(myProject, command);
    TermSize initialTermSize = options.getInitialTermSize();
    try {
      long startNano = System.nanoTime();
      PtyProcessBuilder builder = new PtyProcessBuilder(command)
        .setEnvironment(envs)
        .setDirectory(workingDir)
        .setInitialColumns(initialTermSize != null ? initialTermSize.getColumns() : null)
        .setInitialRows(initialTermSize != null ? initialTermSize.getRows() : null)
        .setUseWinConPty(LocalPtyOptions.shouldUseWinConPty());
      PtyProcess process = builder.start();
      LOG.info("Started " + process.getClass().getName() + " in " + TimeoutUtil.getDurationMillis(startNano) + " ms from "
               + stringifyProcessInfo(command, workingDir, initialTermSize, envs, !LOG.isDebugEnabled()));
      return process;
    }
    catch (IOException e) {
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

  static @NotNull List<String> convertShellPathToCommand(@NotNull String shellPath) {
    List<String> shellCommand = ParametersListUtil.parse(shellPath, false, !SystemInfo.isWindows);
    String shellExe = ContainerUtil.getFirstItem(shellCommand);
    if (shellExe == null) return shellCommand;
    String shellName = PathUtil.getFileName(shellExe);
    if (!containsLoginOrInteractiveOption(shellCommand)) {
      if (isLoginOptionAvailable(shellName) && SystemInfo.isMac) {
        shellCommand.add(LOGIN_CLI_OPTION);
      }
      if (isInteractiveOptionAvailable(shellName)) {
        shellCommand.add(INTERACTIVE_CLI_OPTION);
      }
    }
    return shellCommand;
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
  protected ProcessHandler createProcessHandler(final PtyProcess process) {
    return new PtyProcessHandler(process, getShellPath());
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

  private @NotNull String getShellPath() {
    return TerminalProjectOptionsProvider.getInstance(myProject).getShellPath();
  }

  /** @deprecated to be removed */
  @ApiStatus.Internal
  @Deprecated(forRemoval = true)
  public static @NotNull List<String> getCommand(@NotNull String shellPath,
                                                 @NotNull Map<String, String> envs,
                                                 boolean shellIntegration) {
    List<String> command = convertShellPathToCommand(shellPath);
    return shellIntegration ? injectShellIntegration(command, envs, null) : command;
  }

  static @NotNull List<String> injectShellIntegration(@NotNull List<String> shellCommand,
                                                      @NotNull Map<String, String> envs,
                                                      @Nullable ShellStartupOptions startupOptions) {
    String shellExe = ContainerUtil.getFirstItem(shellCommand);
    if (shellExe == null) return shellCommand;
    return injectShellIntegration(shellExe, shellCommand.subList(1, shellCommand.size()), envs, startupOptions);
  }

  private static @NotNull List<String> injectShellIntegration(@NotNull String shellExe,
                                                              @NotNull List<String> command,
                                                              @NotNull Map<String, String> envs,
                                                              @Nullable ShellStartupOptions startupOptions) {
    List<String> result = new ArrayList<>();
    result.add(shellExe);
    command = new ArrayList<>(command);

    String shellName = PathUtil.getFileName(shellExe);
    String rcFilePath = findRCFile(shellName);
    if (rcFilePath != null) {
      if (shellName.equals(BASH_NAME) || (SystemInfo.isMac && shellName.equals(SH_NAME))) {
        addRcFileArgument(envs, command, result, rcFilePath, "--rcfile");
        // remove --login to enable --rcfile sourcing
        boolean loginShell = command.removeAll(LOGIN_CLI_OPTIONS);
        setLoginShellEnv(envs, loginShell);
        setCommandHistoryFile(startupOptions, envs);
      }
      else if (shellName.equals(ZSH_NAME)) {
        String zdotdir = envs.get(ZDOTDIR);
        if (StringUtil.isNotEmpty(zdotdir)) {
          envs.put("_INTELLIJ_ORIGINAL_ZDOTDIR", zdotdir);
        }
        envs.put(ZDOTDIR, PathUtil.getParentPath(rcFilePath));
      }
      else if (shellName.equals(FISH_NAME)) {
        // `--init-command=COMMANDS` is available since Fish 2.7.0 (released November 23, 2017)
        // Multiple `--init-command=COMMANDS` are supported.
        result.add("--init-command=source " + CommandLineUtil.posixQuote(rcFilePath));
      }
    }

    result.addAll(command);
    return result;
  }

  private static void setCommandHistoryFile(@Nullable ShellStartupOptions startupOptions, @NotNull Map<String, String> envs) {
    Function0<Path> commandHistoryFileProvider = startupOptions != null ? startupOptions.getCommandHistoryFileProvider() : null;
    Path commandHistoryFile = commandHistoryFileProvider != null ? commandHistoryFileProvider.invoke() : null;
    if (commandHistoryFile != null) {
      envs.put(IJ_COMMAND_HISTORY_FILE_ENV, commandHistoryFile.toString());
      ShellTerminalWidget widget = getShellTerminalWidget(startupOptions);
      if (widget != null) {
        widget.setCommandHistoryFilePath(commandHistoryFile.toString());
      }
    }
  }

  private static boolean isLoginOptionAvailable(@NotNull String shellName) {
    return isBashZshFish(shellName);
  }

  private static boolean isInteractiveOptionAvailable(@NotNull String shellName) {
    return isBashZshFish(shellName);
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
                                        List<String> command,
                                        List<String> result,
                                        String rcFilePath, String rcfileOption) {
    result.add(rcfileOption);
    result.add(rcFilePath);
    int idx = command.indexOf(rcfileOption);
    if (idx >= 0) {
      command.remove(idx);
      if (idx < command.size()) {
        envs.put(JEDITERM_USER_RCFILE, FileUtil.expandUserHome(command.get(idx)));
        command.remove(idx);
      }
    }
  }

  private static boolean containsLoginOrInteractiveOption(List<String> command) {
    return isLogin(command) || command.contains(INTERACTIVE_CLI_OPTION);
  }

  private static boolean isLogin(@NotNull List<String> command) {
    return ContainerUtil.exists(command, LOGIN_CLI_OPTIONS::contains);
  }

  private static class PtyProcessHandler extends ProcessHandler implements TaskExecutor {

    private final PtyProcess myProcess;
    private final ProcessWaitFor myWaitFor;

    PtyProcessHandler(PtyProcess process, @NotNull String presentableName) {
      myProcess = process;
      myWaitFor = new ProcessWaitFor(process, this, presentableName);
    }

    @Override
    public void startNotify() {
      addProcessListener(new ProcessAdapter() {
        @Override
        public void startNotified(@NotNull ProcessEvent event) {
          try {
            myWaitFor.setTerminationCallback(integer -> notifyProcessTerminated(integer));
          }
          finally {
            removeProcessListener(this);
          }
        }
      });

      super.startNotify();
    }

    @Override
    protected void destroyProcessImpl() {
      myProcess.destroy();
    }

    @Override
    protected void detachProcessImpl() {
      destroyProcessImpl();
    }

    @Override
    public boolean detachIsDefault() {
      return false;
    }

    @Override
    public boolean isSilentlyDestroyOnClose() {
      return true;
    }

    @Nullable
    @Override
    public OutputStream getProcessInput() {
      return myProcess.getOutputStream();
    }

    @NotNull
    @Override
    public Future<?> executeTask(@NotNull Runnable task) {
      return AppExecutorUtil.getAppExecutorService().submit(task);
    }
  }
}
