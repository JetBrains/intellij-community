package com.jetbrains.python.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ColoredProcessHandler;
import com.intellij.execution.process.RunnerMediator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class PythonProcessHandler extends RunnerMediator.CustomDestroyProcessHandler {

  private PythonProcessHandler(@NotNull Process process, @NotNull GeneralCommandLine commandLine, @NotNull String processUid) {
    super(process, commandLine, processUid);
  }

  @Override
  protected void detachProcessImpl() {
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        long millis = System.currentTimeMillis();
        while (true) {
          try {
            getProcess().exitValue();
            return;
          }
          catch (IllegalThreadStateException e) {
            if (System.currentTimeMillis() - millis > 5000L) {
              if (Messages.showYesNoDialog("Do you want to terminate the process?", "Process is not responding", null) == 0) {
                killProcess();
                return;
              }
              else {
                return;
              }
            }
          }
          try {
            synchronized (this) {
              wait(2000L);
            }
          }
          catch (InterruptedException ignore) {
          }
        }
      }
    }
    );
    super.detachProcessImpl();
  }

  public static ColoredProcessHandler createProcessHandler(GeneralCommandLine commandLine)
    throws ExecutionException {

    String processUid = RunnerMediator.injectUid(commandLine);

    Process p = commandLine.createProcess();

    return new PythonProcessHandler(p, commandLine, processUid);
  }
}
