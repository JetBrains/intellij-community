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
import java.nio.charset.Charset;
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

  private static File findRCFile() {
    try {
      final String folder = PtyUtil.getPtyLibFolderPath();
      if (folder != null) {
        File rcFile = new File(folder, "jediterm.in");
        if (rcFile.exists()) {
          return rcFile;
        }
      }
    }
    catch (Exception e) {
      LOG.warn("Unable to get JAR folder", e);
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
    
    for (LocalTerminalCustomizer customizer: LocalTerminalCustomizer.EP_NAME.getExtensions()) {
      customizer.setupEnvironment(myProject, envs);
    }
    
    try {
      return PtyProcess.exec(getCommand(), envs, directory != null ? directory : currentProjectFolder());
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
    String[] command;
    String shellPath = TerminalOptionsProvider.getInstance().getShellPath();

    if (SystemInfo.isUnix) {
      File rcFile = findRCFile();

      String shellName = getShellName(shellPath);

      if (rcFile != null && (shellName.equals("bash") || shellName.equals("sh"))) {
        command = new String[]{shellPath, "--rcfile", rcFile.getAbsolutePath(), "-i"};
      }
      else if (hasLoginArgument(shellName)) {
        command = new String[]{shellPath, "--login"};
      }
      else {
        command = shellPath.split(" ");
      }
    }
    else {
      command = new String[]{shellPath};
    }

    return command;
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
