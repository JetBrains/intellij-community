package com.jetbrains.python.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class JythonProcessHandler extends PythonProcessHandler {
  private JythonProcessHandler(@NotNull Process process, @NotNull GeneralCommandLine commandLine) {
    super(process, commandLine);
  }

  @Override
  protected void doDestroyProcess() {
    // force "kill -9" because jython makes threaddump on "SIGINT" signal
    killProcessTree(getProcess());
  }

  public static JythonProcessHandler createProcessHandler(GeneralCommandLine commandLine)
    throws ExecutionException {

    Process p = commandLine.createProcess();

    return new JythonProcessHandler(p, commandLine);
  }
}
