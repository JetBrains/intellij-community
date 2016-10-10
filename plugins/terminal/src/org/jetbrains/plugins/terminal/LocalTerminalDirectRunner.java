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
import com.intellij.execution.configurations.EncodingEnvironmentUtil;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessWaitFor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.HashMap;
import com.jediterm.pty.PtyProcessTtyConnector;
import com.jediterm.terminal.TtyConnector;
import com.pty4j.PtyProcess;
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

  private final Charset myDefaultCharset;

  public LocalTerminalDirectRunner(Project project) {
    super(project);
    myDefaultCharset = CharsetToolkit.UTF8_CHARSET;
  }

  private static boolean hasLoginArgument(String name) {
    return name.equals("bash") || name.equals("sh") || name.equals("zsh");
  }

  private static String getShellName(String path) {
    return new File(path).getName();
  }

  private static String findRCFile(String shellName) {
    if (shellName != null) {
      if ("bash".equals(shellName)) {
        shellName = "sh";
      }
      try {

        URL resource = LocalTerminalDirectRunner.class.getClassLoader().getResource("jediterm-" + shellName + ".in");
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
    EncodingEnvironmentUtil.setLocaleEnvironmentIfMac(envs, myDefaultCharset);

    String[] command = getCommand();

    for (LocalTerminalCustomizer customizer : LocalTerminalCustomizer.EP_NAME.getExtensions()) {
      command = customizer.customizeCommandAndEnvironment(myProject, command, envs);

      if (directory == null) {
        directory = customizer.getDefaultFolder();
      }
    }

    try {
      return PtyProcess.exec(command, envs, directory != null ? directory : currentProjectFolder());
    }
    catch (IOException e) {
      throw new ExecutionException(e);
    }
  }

  private String currentProjectFolder() {
    final ProjectRootManager projectRootManager = ProjectRootManager.getInstance(myProject);

    final VirtualFile[] roots = projectRootManager.getContentRoots();
    if (roots.length == 1) {
      roots[0].getCanonicalPath();
    }
    final VirtualFile baseDir = myProject.getBaseDir();
    return baseDir == null ? null : baseDir.getCanonicalPath();
  }

  @Override
  protected ProcessHandler createProcessHandler(final PtyProcess process) {
    return new PtyProcessHandler(process, getCommand()[0]);
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


  public String[] getCommand() {

    String shellPath = TerminalOptionsProvider.getInstance().getShellPath();

    return getCommand(shellPath);
  }

  @NotNull
  public static String[] getCommand(String shellPath) {
    if (SystemInfo.isUnix) {
      List<String> command = Lists.newArrayList(shellPath.split(" "));

      String shellCommand = command.get(0);
      String shellName = command.size() > 0 ? getShellName(shellCommand) : null;


      if (shellName != null) {
        command.remove(0);

        List<String> result = Lists.newArrayList(shellCommand);

        String rcFilePath = findRCFile(shellName);


        if (rcFilePath != null &&
            TerminalOptionsProvider.getInstance().shellIntegration() &&
            (shellName.equals("bash") || shellName.equals("sh"))) {
          result.add("--rcfile");
          result.add(rcFilePath);
        }

        if (!loginOrInteractive(command)) {
          if (hasLoginArgument(shellName) && SystemInfo.isMac) {
            result.add("--login");
          }
          result.add("-i");
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

  private static boolean loginOrInteractive(List<String> command) {
    return command.contains("-i") || command.contains("--login") || command.contains("-l");
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
        public void startNotified(ProcessEvent event) {
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
