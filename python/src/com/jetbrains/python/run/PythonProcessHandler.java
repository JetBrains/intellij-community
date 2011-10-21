package com.jetbrains.python.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.KillableColoredProcessHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

/**
 * @author traff
 */
public class PythonProcessHandler extends KillableColoredProcessHandler {
  protected PythonProcessHandler(@NotNull Process process, @NotNull GeneralCommandLine commandLine) {
    super(process, commandLine.getCommandLineString());
  }

  public PythonProcessHandler(Process process, String commandLine, @Nullable Charset charset) {
    super(process, commandLine, charset);
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
