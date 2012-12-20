package com.jetbrains.python.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.KillableColoredProcessHandler;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;

/**
 * @author traff
 */
public class PythonProcessHandler extends KillableColoredProcessHandler {
  private boolean myShouldTryToKillSoftly = true;

  protected PythonProcessHandler(@NotNull Process process, @NotNull GeneralCommandLine commandLine) {
    super(process, commandLine.getCommandLineString());
  }

  public PythonProcessHandler(Process process, String commandLine, @NotNull Charset charset) {
    super(process, commandLine, charset);
  }

  public void setShouldTryToKillSoftly(boolean shouldTryToKillSoftly) {
    myShouldTryToKillSoftly = shouldTryToKillSoftly;
  }

  @Override
  protected boolean shouldKillProcessSoftly() {
    return myShouldTryToKillSoftly;
  }

  @Override
  protected boolean shouldDestroyProcessRecursively() {
    return true;
  }

  public static PythonProcessHandler createProcessHandler(GeneralCommandLine commandLine)
    throws ExecutionException {

    Process p = commandLine.createProcess();

    return new PythonProcessHandler(p, commandLine);
  }
}
