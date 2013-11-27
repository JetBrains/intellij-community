package org.jetbrains.plugins.terminal;

import com.intellij.execution.TaskExecutor;
import com.intellij.execution.process.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.containers.HashMap;
import com.jediterm.pty.PtyProcessTtyConnector;
import com.jediterm.terminal.TtyConnector;
import com.pty4j.PtyProcess;
import com.pty4j.util.PtyUtil;
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
    myDefaultCharset = Charset.forName("UTF-8");
  }

  private static boolean hasLoginArgument(String name) {
    return name.equals("bash") || name.equals("sh") || name.equals("zsh");
  }

  private static String getShellName(String path) {
    return new File(path).getName();
  }

  private static File findRCFile() {
    try {
      final String folder = PtyUtil.getJarFolder();
      if (folder != null) {
        File rcFile = new File(folder, "jediterm.in");
        if (rcFile.exists()) {
          return rcFile;
        }
      }
    }
    catch (Exception e) {
      LOG.warn("Unable to get jar folder", e);
    }
    return null;
  }

  @Override
  protected PtyProcess createProcess() throws ExecutionException {
    Map<String, String> envs = new HashMap<String, String>(System.getenv());
    envs.put("TERM", "xterm");
    try {
      return PtyProcess.exec(getCommand(), envs, currentProjectFolder());
    }
    catch (IOException e) {
      throw new ExecutionException(e);
    }
  }

  private String currentProjectFolder() {
    for (VirtualFile vf : ProjectRootManager.getInstance(myProject).getContentRoots()) {
      return vf.getCanonicalPath();
    }
    return null;
  }

  @Override
  protected ProcessHandler createProcessHandler(final PtyProcess process) {
    return new PtyProcessHandler(process);
  }

  @Override
  protected TtyConnector createTtyConnector(PtyProcess process) {
    return new PtyProcessTtyConnector(process, myDefaultCharset);
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

    public PtyProcessHandler(PtyProcess process) {
      myProcess = process;
      myWaitFor = new ProcessWaitFor(process, this);
    }

    @Override
    public void startNotify() {
      addProcessListener(new ProcessAdapter() {
        @Override
        public void startNotified(ProcessEvent event) {
          try {
            myWaitFor.setTerminationCallback(new Consumer<Integer>() {
              @Override
              public void consume(Integer integer) {
                notifyProcessTerminated(integer);
              }
            });
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

    @Override
    public Future<?> executeTask(Runnable task) {
      return executeOnPooledThread(task);
    }

    protected static Future<?> executeOnPooledThread(Runnable task) {
      final Application application = ApplicationManager.getApplication();

      if (application != null) {
        return application.executeOnPooledThread(task);
      }

      return BaseOSProcessHandler.ExecutorServiceHolder.submit(task);
    }
  }
}
