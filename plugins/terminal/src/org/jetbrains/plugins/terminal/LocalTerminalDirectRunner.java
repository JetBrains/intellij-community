/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.terminal;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.execution.TaskExecutor;
import com.intellij.execution.configuration.EnvironmentVariablesData;
import com.intellij.execution.process.*;
import com.intellij.internal.statistic.service.fus.collectors.FUSUsageContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.ArrayUtil;
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

  private final Charset myDefaultCharset;

  public LocalTerminalDirectRunner(Project project) {
    super(project);
    myDefaultCharset = CharsetToolkit.UTF8_CHARSET;
  }

  private static boolean hasLoginArgument(String name) {
    return name.equals("bash") || name.equals("sh") || name.equals("zsh");
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
  private static String findRCFile(String shellName) {
    if (shellName != null) {
      if ("sh".equals(shellName)) {
        shellName = "bash";
      }
      String rcfile = "jediterm-" + shellName + ".in";
      if ("zsh".equals(shellName)) {
        rcfile = ".zshrc";
      }
      else if ("fish".equals(shellName)) {
        rcfile = "fish/config.fish";
      }
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

    try {
      TerminalUsageTriggerCollector.trigger(myProject, "local.exec", FUSUsageContext.create(
        FUSUsageContext.getOSNameContextData(),
        SystemInfo.getOsNameAndVersion(),
        TerminalUsageTriggerCollector.getShellNameForStat(command[0]))
      );
      String workingDir = getWorkingDirectory(directory);
      long startNano = System.nanoTime();
      String[] finalCommand = command;
      PtyProcess process = TerminalSignalUtil.computeWithIgnoredSignalsResetToDefault(
        new int[] {UnixProcessManager.SIGINT, TerminalSignalUtil.SIGQUIT, TerminalSignalUtil.SIGPIPE},
        () -> PtyProcess.exec(finalCommand, envs, workingDir)
      );
      if (LOG.isDebugEnabled()) {
        LOG.debug("Started " + process.getClass().getName() + " from " + Arrays.toString(command) + " in " + workingDir +
                  " (" + TimeoutUtil.getDurationMillis(startNano) + " ms)");
      }
      return process;
    }
    catch (IOException e) {
      throw new ExecutionException("Failed to start " + Arrays.toString(command), e);
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

        if (!loginOrInteractive(command)) {
          if (hasLoginArgument(shellName) && SystemInfo.isMac) {
            command.add(LOGIN_CLI_OPTION);
          }
          command.add("-i");
        }

        List<String> result = Lists.newArrayList(shellCommand);

        String rcFilePath = findRCFile(shellName);

        if (rcFilePath != null && shellIntegration) {
          if (shellName.equals("bash") || (SystemInfo.isMac && shellName.equals("sh"))) {
            addRcFileArgument(envs, command, result, rcFilePath, "--rcfile");
            // remove --login to enable --rcfile sourcing
            boolean loginShell = command.removeAll(LOGIN_CLI_OPTIONS);
            setLoginShellEnv(envs, loginShell);
          }
          else if (shellName.equals("zsh")) {
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
          else if (shellName.equals("fish")) {
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
        return ArrayUtil.toStringArray(result);
      }
      else {
        return ArrayUtil.toStringArray(command);
      }
    }
    else {
      return new String[]{shellPath};
    }
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

  private static boolean loginOrInteractive(List<String> command) {
    return command.contains("-i") || isLogin(command);
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
