package com.jetbrains.python.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;
import com.jetbrains.python.sdk.PythonSdkFlavor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

/**
 * @author traff
 */
public class PythonProcessHandler extends ColoredProcessHandler {
  protected PythonProcessHandler(@NotNull Process process, @NotNull GeneralCommandLine commandLine) {
    super(process, commandLine.getCommandLineString());
  }

  public PythonProcessHandler(Process process, String commandLine, @Nullable Charset charset) {
    super(process, commandLine, charset);
  }

  @Override
  protected void destroyProcessImpl() {
    super.destroyProcessImpl();
    if (RunnerMediator.isUnix()) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
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
                  UnixProcessManager.sendSigKillToProcessTree(getProcess());
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
    }
  }

  public void killProcess() {
    if (!killProcessTree(getProcess())) {
      closeStreamsAndDestroyProcess();
    }
  }

  public static PythonProcessHandler createProcessHandler(GeneralCommandLine commandLine)
    throws ExecutionException {

    Process p = commandLine.createProcess();

    return new PythonProcessHandler(p, commandLine);
  }

  private static boolean killProcessTree(final Process process) {
    return OSProcessManager.getInstance().killProcessTree(process);
  }
}
