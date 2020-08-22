// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import org.jetbrains.annotations.NotNull;

public final class JythonProcessHandler extends PythonProcessHandler {
  private JythonProcessHandler(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    super(commandLine);
  }

  @Override
  protected void doDestroyProcess() {
    // force "kill -9" because jython makes threaddump on "SIGINT" signal
    killProcessTree(getProcess());
  }

  public static JythonProcessHandler createProcessHandler(GeneralCommandLine commandLine)
    throws ExecutionException {

    return new JythonProcessHandler(commandLine);
  }
}
