package org.jetbrains.plugins.terminal;

import com.intellij.execution.TaskExecutor;
import com.intellij.execution.process.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import com.intellij.util.containers.HashMap;
import com.jediterm.pty.PtyProcessTtyConnector;
import com.jediterm.terminal.TtyConnector;
import com.pty4j.PtyProcess;
import org.jetbrains.annotations.Nullable;

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

  private final Charset myDefaultCharset;
  private final String[] myCommand;

  public LocalTerminalDirectRunner(Project project, String[] command) {
    super(project);
    myDefaultCharset = Charset.forName("UTF-8");
    myCommand = command;
  }

  @Override
  protected PtyProcess createProcess() throws ExecutionException {
    Map<String, String> envs = new HashMap<String, String>(System.getenv());
    envs.put("TERM", "xterm");
    try {
      return PtyProcess.exec(myCommand, envs, null);
    }
    catch (IOException e) {
      throw new ExecutionException(e);
    }
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
    return StringUtil.join(myCommand);
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
