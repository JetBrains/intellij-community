package com.jetbrains.python.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.RunnerMediator;
import com.jetbrains.python.sdk.JythonSdkFlavor;
import com.jetbrains.python.sdk.PythonSdkFlavor;

/**
 * @author traff
 */
public class PythonProcessRunner {
  private PythonProcessRunner() {
  }

  public static ProcessHandler createProcess(GeneralCommandLine commandLine, boolean withMediator) throws ExecutionException {
    if (PythonSdkFlavor.getFlavor(commandLine.getExePath()) instanceof JythonSdkFlavor) {
      return JythonProcessHandler.createProcessHandler(commandLine);
    }
    else {
      if (withMediator) {
        return RunnerMediator.getInstance().createProcess(commandLine);
      }
      else {
        return PythonProcessHandler.createProcessHandler(commandLine);
      }
    }
  }

  public static ProcessHandler createProcess(GeneralCommandLine commandLine) throws ExecutionException {
    return createProcess(commandLine, false);
  }

  public static ProcessHandler createProcessHandlingCtrlC(GeneralCommandLine commandLine) throws ExecutionException {
    return createProcess(commandLine, true);
  }

}
