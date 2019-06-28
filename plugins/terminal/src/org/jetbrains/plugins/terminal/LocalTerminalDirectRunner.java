// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.execution.TaskExecutor;
import com.intellij.execution.configuration.EnvironmentVariablesData;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessWaitFor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CaseInsensitiveStringHashingStrategy;
import com.jediterm.pty.PtyProcessTtyConnector;
import com.jediterm.terminal.TtyConnector;
import com.pty4j.PtyProcess;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * @author traff
 */
public class LocalTerminalDirectRunner extends AbstractTerminalRunner<PtyProcess> {
  private static final Logger LOG = Logger.getInstance(LocalTerminalDirectRunner.class);
  private static final String JEDITERM_USER_RCFILE = "JEDITERM_USER_RCFILE";
  private static final String ZDOTDIR = "ZDOTDIR";
  private static final String XDG_CONFIG_HOME = "XDG_CONFIG_HOME";
  private static final String IJ_COMMAND_HISTORY_FILE_ENV = "__INTELLIJ_COMMAND_HISTFILE__";
  private static final String LOGIN_SHELL = "LOGIN_SHELL";
  private static final ImmutableList<String> LOGIN_CLI_OPTIONS = ImmutableList.of("--login", "-l");
  private static final String LOGIN_CLI_OPTION = LOGIN_CLI_OPTIONS.get(0);
  private static final String INTERACTIVE_CLI_OPTION = "-i";
  private static final String BASH_NAME = "bash";
  private static final String SH_NAME = "sh";
  private static final String ZSH_NAME = "zsh";
  private static final String FISH_NAME = "fish";

  private final Charset myDefaultCharset;

  public LocalTerminalDirectRunner(Project project) {
    super(project);
    myDefaultCharset = StandardCharsets.UTF_8;
  }

  private static String getShellName(@Nullable String path) {
    if (path == null) {
      return null;
    }
    else {
      return new File(path).getName();
    }
  }

  @Nullable
  private static String findRCFile(@NotNull String shellName) {
    String rcfile = null;
    //noinspection IfCanBeSwitch
    if (BASH_NAME.equals(shellName) || SH_NAME.equals(shellName)) {
      rcfile = "jediterm-bash.in";
    }
    else if (ZSH_NAME.equals(shellName)) {
      rcfile = ".zshrc";
    }
    else if (FISH_NAME.equals(shellName)) {
      rcfile = "fish/config.fish";
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
      if (ApplicationManager.getApplication().isInternal()) {
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


  private Map<String, String> getTerminalEnvironment() {
    Map<String, String> envs = new THashMap<>(SystemInfo.isWindows ? CaseInsensitiveStringHashingStrategy.INSTANCE
                                                                     : ContainerUtil.canonicalStrategy());

    EnvironmentVariablesData envData = TerminalOptionsProvider.getInstance().getEnvData();
    if (envData.isPassParentEnvs()) {
      envs.putAll(System.getenv());
    }

    if (!SystemInfo.isWindows) {
      envs.put("TERM", "xterm-256color");
    }
    envs.put("TERMINAL_EMULATOR", "JetBrains-JediTerm");

    if (SystemInfo.isMac) {
      EnvironmentUtil.setLocaleEnv(envs, myDefaultCharset);
    }

    PathMacroManager macroManager = PathMacroManager.getInstance(myProject);
    for (Map.Entry<String, String> env : envData.getEnvs().entrySet()) {
      envs.put(env.getKey(), macroManager.expandPath(env.getValue()));
    }
    return envs;
  }

  @Override
  protected PtyProcess createProcess(@Nullable String directory) throws ExecutionException {
    return createProcess(directory, null);
  }

  @Override
  protected PtyProcess createProcess(@Nullable String directory, @Nullable String commandHistoryFilePath) throws ExecutionException {
    Map<String, String> envs = getTerminalEnvironment();

    String[] command = getCommand(envs);

    for (LocalTerminalCustomizer customizer : LocalTerminalCustomizer.EP_NAME.getExtensions()) {
      try {
        command = customizer.customizeCommandAndEnvironment(myProject, command, envs);
      }
      catch (Exception e) {
        LOG.error("Exception during customization of the terminal session", e);
      }
    }
    if (commandHistoryFilePath != null) {
      envs.put(IJ_COMMAND_HISTORY_FILE_ENV, commandHistoryFilePath);
    }

    String workingDir = getWorkingDirectory(directory);
    TerminalUsageTriggerCollector.triggerLocalShellStarted(myProject, command);
    try {
      long startNano = System.nanoTime();
      PtyProcess process = PtyProcess.exec(command, envs, workingDir);
      if (LOG.isDebugEnabled()) {
        LOG.debug("Started " + process.getClass().getName() + " from " + Arrays.toString(command) + " in " + workingDir +
                  " (" + TimeoutUtil.getDurationMillis(startNano) + " ms)");
      }
      return process;
    }
    catch (IOException e) {
      throw new ExecutionException("Failed to start " + Arrays.toString(command) + " in " + workingDir, e);
    }
  }

  @Nullable
  private String getWorkingDirectory(@Nullable String directory) {
    if (directory != null) return directory;
    return TerminalProjectOptionsProvider.getInstance(myProject).getStartingDirectory();
  }

  @Override
  protected ProcessHandler createProcessHandler(final PtyProcess process) {
    return new PtyProcessHandler(process, getShellPath());
  }

  @Override
  protected TtyConnector createTtyConnector(PtyProcess process) {
    return new PtyProcessTtyConnector(process, myDefaultCharset) {
      @Override
      protected void resizeImmediately() {
        if (LOG.isDebugEnabled()) {
          LOG.debug("resizeImmediately to " + getPendingTermSize());
        }
        super.resizeImmediately();
      }
    };
  }

  @Override
  public String runningTargetName() {
    return "Local Terminal";
  }

  @Override
  protected String getTerminalConnectionName(PtyProcess process) {
    return "Local Terminal";
  }


  public String[] getCommand(Map<String, String> envs) {

    String shellPath = getShellPath();

    return getCommand(shellPath, envs, TerminalOptionsProvider.getInstance().shellIntegration());
  }

  private static String getShellPath() {
    return TerminalOptionsProvider.getInstance().getShellPath();
  }

  @NotNull
  public static String[] getCommand(String shellPath, Map<String, String> envs, boolean shellIntegration) {
    if (SystemInfo.isUnix) {
      List<String> command = Lists.newArrayList(shellPath.split(" "));

      String shellCommand = command.size() > 0 ? command.get(0) : null;
      String shellName = getShellName(shellCommand);

      if (shellName != null) {
        command.remove(0);

        if (!containsLoginOrInteractiveOption(command)) {
          if (isLoginOptionAvailable(shellName) && SystemInfo.isMac) {
            command.add(LOGIN_CLI_OPTION);
          }
          if (isInteractiveOptionAvailable(shellName)) {
            command.add(INTERACTIVE_CLI_OPTION);
          }
        }

        List<String> result = Lists.newArrayList(shellCommand);

        String rcFilePath = findRCFile(shellName);

        if (rcFilePath != null && shellIntegration) {
          if (shellName.equals(BASH_NAME) || (SystemInfo.isMac && shellName.equals(SH_NAME))) {
            addRcFileArgument(envs, command, result, rcFilePath, "--rcfile");
            // remove --login to enable --rcfile sourcing
            boolean loginShell = command.removeAll(LOGIN_CLI_OPTIONS);
            setLoginShellEnv(envs, loginShell);
          }
          else if (shellName.equals(ZSH_NAME)) {
            String zdotdir = EnvironmentUtil.getEnvironmentMap().get(ZDOTDIR);
            if (StringUtil.isNotEmpty(zdotdir)) {
              envs.put("_OLD_ZDOTDIR", zdotdir);
              File zshRc = new File(FileUtil.expandUserHome(zdotdir), ".zshrc");
              if (zshRc.exists()) {
                envs.put(JEDITERM_USER_RCFILE, zshRc.getAbsolutePath());
              }
            }
            envs.put(ZDOTDIR, new File(rcFilePath).getParent());
          }
          else if (shellName.equals(FISH_NAME)) {
            String xdgConfig = EnvironmentUtil.getEnvironmentMap().get(XDG_CONFIG_HOME);
            if (StringUtil.isNotEmpty(xdgConfig)) {
              File fishConfig = new File(new File(FileUtil.expandUserHome(xdgConfig), "fish"), "config.fish");
              if (fishConfig.exists()) {
                envs.put(JEDITERM_USER_RCFILE, fishConfig.getAbsolutePath());
              }
              envs.put("OLD_" + XDG_CONFIG_HOME, xdgConfig);
            }

            envs.put(XDG_CONFIG_HOME, new File(rcFilePath).getParentFile().getParent());
          }
        }

        setLoginShellEnv(envs, isLogin(command));

        result.addAll(command);
        return ArrayUtilRt.toStringArray(result);
      }
      else {
        return ArrayUtilRt.toStringArray(command);
      }
    }
    else {
      return new String[]{shellPath};
    }
  }

  private static boolean isLoginOptionAvailable(@NotNull String shellName) {
    return shellName.equals(BASH_NAME) || (SystemInfo.isMac && shellName.equals(SH_NAME)) || shellName.equals(ZSH_NAME);
  }

  private static boolean isInteractiveOptionAvailable(@NotNull String shellName) {
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
    return command.stream().anyMatch(s -> LOGIN_CLI_OPTIONS.contains(s));
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
