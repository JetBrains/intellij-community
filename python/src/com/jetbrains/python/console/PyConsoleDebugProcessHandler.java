// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console;

import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.Key;
import com.jetbrains.python.debugger.PositionConverterProvider;
import com.jetbrains.python.debugger.PyDebugProcess;
import com.jetbrains.python.debugger.PyLocalPositionConverter;
import com.jetbrains.python.debugger.PyPositionConverter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.OutputStream;

public class PyConsoleDebugProcessHandler extends ProcessHandler implements PositionConverterProvider {
  private final PyConsoleProcessHandler myConsoleProcessHandler;

  public PyConsoleDebugProcessHandler(final PyConsoleProcessHandler processHandler) {
    myConsoleProcessHandler = processHandler;
    processHandler.addProcessListener(new ProcessListener() {
      @Override
      public void startNotified(@NotNull ProcessEvent event) {

      }

      @Override
      public void processTerminated(@NotNull ProcessEvent event) {
        PyConsoleDebugProcessHandler.this.notifyProcessTerminated(event.getExitCode());
      }

      @Override
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        PyConsoleDebugProcessHandler.this.notifyTextAvailable(event.getText(), outputType);
      }
    });
  }

  @Override
  protected void destroyProcessImpl() {
    detachProcessImpl();
  }

  @Override
  protected void detachProcessImpl() {
    notifyProcessTerminated(0);
    notifyTextAvailable("Debugger disconnected.\n", ProcessOutputTypes.SYSTEM);
  }

  @Override
  public boolean detachIsDefault() {
    return false;
  }

  @Override
  public OutputStream getProcessInput() {
    return null;
  }

  public PyConsoleProcessHandler getConsoleProcessHandler() {
    return myConsoleProcessHandler;
  }

  @Nullable
  @Override
  public PyPositionConverter createPositionConverter(@NotNull PyDebugProcess debugProcess) {
    if (myConsoleProcessHandler instanceof PositionConverterProvider) {
      return ((PositionConverterProvider)myConsoleProcessHandler).createPositionConverter(debugProcess);
    }
    else {
      return new PyLocalPositionConverter();
    }
  }
}
