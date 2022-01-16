// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;

public class PythonProcessHandler extends KillableColoredProcessHandler {

  public static boolean softKillOnWin() {
    return Registry.is("kill.windows.processes.softly", false);
  }

  public PythonProcessHandler(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    super(commandLine);
  }

  public PythonProcessHandler(Process process, @NotNull String commandLine, @NotNull Charset charset) {
    super(process, commandLine, charset);
  }

  @Override
  protected boolean shouldDestroyProcessRecursively() {
    return true;
  }
}
