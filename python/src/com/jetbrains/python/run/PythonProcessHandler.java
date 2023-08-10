// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.AnsiEscapeDecoder;
import com.intellij.execution.process.KillableProcessHandler;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;

public class PythonProcessHandler extends KillableProcessHandler implements AnsiEscapeDecoder.ColoredTextAcceptor {

  private final AnsiEscapeDecoder myAnsiEscapeDecoder = new AnsiEscapeDecoder();

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

  @Override
  public final void notifyTextAvailable(@NotNull final String text, @NotNull final Key outputType) {
    if (hasPty()) {
      super.notifyTextAvailable(text, outputType);
    } else {
      myAnsiEscapeDecoder.escapeText(text, outputType, this);
    }
  }

  @Override
  public void coloredTextAvailable(@NotNull String text, @NotNull Key attributes) {
    super.notifyTextAvailable(text, attributes);
  }
}
