// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.execution.process.ProcessHandler;
import com.jetbrains.python.sdk.flavors.JythonSdkFlavor;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;

public final class PythonProcessRunner {
  private PythonProcessRunner() {
  }

  public static ProcessHandler createProcess(GeneralCommandLine commandLine, boolean softKillOnWin) throws ExecutionException {
    if (PythonSdkFlavor.getFlavor(commandLine.getExePath()) instanceof JythonSdkFlavor) {
      return JythonProcessHandler.createProcessHandler(commandLine);
    }
    else {
      if (isUnderDebugger(commandLine)) {
        return new PyDebugProcessHandler(commandLine);
      }
      return new PythonProcessHandler(commandLine);
    }
  }

  public static ProcessHandler createProcess(GeneralCommandLine commandLine) throws ExecutionException {
    return createProcess(commandLine, PythonProcessHandler.softKillOnWin());
  }

  public static ProcessHandler createProcessHandlingCtrlC(GeneralCommandLine commandLine) throws ExecutionException {
    return createProcess(commandLine, true);
  }

  private static boolean isUnderDebugger(GeneralCommandLine commandLine) {
    ParamsGroup debugParams = commandLine.getParametersList().getParamsGroup(PythonCommandLineState.GROUP_DEBUGGER);
    return debugParams != null && debugParams.getParameters().size() > 0;
  }
}
