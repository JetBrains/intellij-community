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

import com.google.common.collect.Lists;
import com.intellij.execution.TaskExecutor;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessWaitFor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.ArrayUtil;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.HashMap;
import com.jediterm.pty.PtyProcessTtyConnector;
import com.jediterm.terminal.TtyConnector;
import com.pty4j.PtyProcess;
import com.pty4j.util.PtyUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * @author traff
 */
public class LocalTerminalDirectRunner extends AbstractTerminalRunner<PtyProcess> {
  private static final Logger LOG = Logger.getInstance(LocalTerminalDirectRunner.class);
  public static final String JEDITERM_USER_RCFILE = "JEDITERM_USER_RCFILE";
  public static final String ZDOTDIR = "ZDOTDIR";
  public static final String XDG_CONFIG_HOME = "XDG_CONFIG_HOME";


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

  private static String findRCFile(String shellName) {
    if (shellName != null) {
      if ("sh".equals(shellName)) {
        shellName = "bash";
      }
      try {
        String rcfile = "jediterm-" + shellName + ".in";
        if ("zsh".equals(shellName)) {
          rcfile = ".zshrc";
        }
        else if ("fish".equals(shellName)) {
          rcfile = "fish/config.fish";
        }
        URL resource = LocalTerminalDirectRunner.class.getClassLoader().getResource(rcfile);
        if (resource != null && "jar".equals(resource.getProtocol())) {
          File file = new File(new File(PtyUtil.getJarContainingFolderPath(LocalTerminalDirectRunner.class)).getParent(), rcfile);
          if (file.exists()) {
            return file.getAbsolutePath();
          }
        }
        if (resource != null) {
          URI uri = resource.toURI();
          return uri.getPath();
        }
      }
      catch (Exception e) {
        LOG.warn("Unable to find " + "jediterm-" + shellName + ".in configuration file", e);
      }
    }
    return null;
  }

  @NotNull
  public static LocalTerminalDirectRunner createTerminalRunner(Project project) {
    return new LocalTerminalDirectRunner(project);
  }

  @Override
  protected PtyProcess createProcess(@Nullable String directory) throws ExecutionException {
    Map<String, String> envs = new HashMap<>(System.getenv());
    if (!SystemInfo.isWindows) {
      envs.put("TERM", "xterm-256color");
    }

    if (SystemInfo.isMac) {
      EnvironmentUtil.setLocaleEnv(envs, myDefaultCharset);
    }

    String[] command = getCommand(envs);

    for (LocalTerminalCustomizer customizer : LocalTerminalCustomizer.EP_NAME.getExtensions()) {
      try {
        command = customizer.customizeCommandAndEnvironment(myProject, command, envs);

        if (directory == null) {
          directory = customizer.getDefaultFolder(myProject);
        }
      }
      catch (Exception e) {
        LOG.error("Exception during customization of the terminal session", e);
      }
    }

    try {
      return PtyProcess.exec(command, envs, directory != null
                                            ? directory
                                            : TerminalProjectOptionsProvider.Companion.getInstance(myProject).getStartingDirectory());
    }
    catch (IOException e) {
      throw new ExecutionException(e);
    }
  }

  @Override
  protected ProcessHandler createProcessHandler(final PtyProcess process) {
    return new PtyProcessHandler(process, getShellPath());
  }

  @Override
  protected TtyConnector createTtyConnector(PtyProcess process) {
    return new PtyProcessTtyConnector(process, myDefaultCharset);
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

    return getCommand(shellPath, envs, TerminalOptionsProvider.Companion.getInstance().shellIntegration());
  }

  private static String getShellPath() {
    return TerminalOptionsProvider.Companion.getInstance().getShellPath();
  }

  @NotNull
  public static String[] getCommand(String shellPath, Map<String, String> envs, boolean shellIntegration) {
    if (SystemInfo.isUnix) {
      List<String> command = Lists.newArrayList(shellPath.split(" "));

      String shellCommand = command.size() > 0 ? command.get(0) : null;
      String shellName = getShellName(shellCommand);

      if (shellName != null) {
        command.remove(0);

        List<String> result = Lists.newArrayList(shellCommand);

        String rcFilePath = findRCFile(shellName);

        if (rcFilePath != null &&
            shellIntegration) {
          if (shellName.equals("bash") || (SystemInfo.isMac && shellName.equals("sh"))) {
            addRcFileArgument(envs, command, result, rcFilePath, "--rcfile");
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

        if (!loginOrInteractive(command)) {
          if (hasLoginArgument(shellName) && SystemInfo.isMac) {
            result.add("--login");
          }
          result.add("-i");
        }

        if (isLogin(command)) {
          envs.put("LOGIN_SHELL", "1");
        }

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

  private static boolean isLogin(List<String> command) {
    return command.contains("--login") || command.contains("-l");
  }

  private static class PtyProcessHandler extends ProcessHandler implements TaskExecutor {

    private final PtyProcess myProcess;
    private final ProcessWaitFor myWaitFor;

    public PtyProcessHandler(PtyProcess process, @NotNull String presentableName) {
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
