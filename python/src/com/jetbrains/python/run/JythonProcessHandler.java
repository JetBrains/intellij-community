package com.jetbrains.python.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.KillableProcess;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ColoredProcessHandler;
import com.intellij.execution.process.OSProcessManager;
import com.intellij.execution.process.RunnerMediator;
import com.intellij.execution.process.UnixProcessManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

/**
 * @author traff
 */
public class JythonProcessHandler extends PythonProcessHandler implements KillableProcess {
  private JythonProcessHandler(@NotNull Process process, @NotNull GeneralCommandLine commandLine) {
    super(process, commandLine);
  }

  public JythonProcessHandler(Process process, String commandLine, @Nullable Charset charset) {
    super(process, commandLine, charset);
  }

  @Override
  protected void destroyProcessImpl() {
    killProcess();
  }

  @Override
  public boolean canKillProcess() {
    return true;
  }

  public static JythonProcessHandler createProcessHandler(GeneralCommandLine commandLine)
    throws ExecutionException {

    Process p = commandLine.createProcess();

    return new JythonProcessHandler(p, commandLine);
  }
}
