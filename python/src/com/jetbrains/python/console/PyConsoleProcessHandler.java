// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.console;

import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.util.io.BaseOutputReader;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.run.PythonProcessHandler;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;

public class PyConsoleProcessHandler extends PythonProcessHandler {
  private final PythonConsoleView myConsoleView;
  private final PydevConsoleCommunication myPydevConsoleCommunication;

  public PyConsoleProcessHandler(final Process process,
                                 @NotNull PythonConsoleView consoleView,
                                 @NotNull PydevConsoleCommunication pydevConsoleCommunication,
                                 @NotNull String commandLine,
                                 @NotNull Charset charset) {
    super(process, commandLine, charset);
    myConsoleView = consoleView;
    myPydevConsoleCommunication = pydevConsoleCommunication;

    Disposer.register(consoleView, new Disposable() {
      @Override
      public void dispose() {
        if (!isProcessTerminated()) {
          destroyProcess();
        }
      }
    });
    addProcessListener(new ProcessListener() {
      @Override
      public void processTerminated(@NotNull ProcessEvent event) {
        doCloseCommunication();
      }
    });
  }

  @Override
  public void coloredTextAvailable(@NotNull final String text, @NotNull final Key attributes) {
    String string = PyConsoleUtil.processPrompts(myConsoleView, text);
    super.coloredTextAvailable(string, attributes);
  }

  @Override
  public boolean isSilentlyDestroyOnClose() {
    return !myPydevConsoleCommunication.isExecuting();
  }

  @Override
  public boolean shouldKillProcessSoftly() {
    return false;
  }

  @NotNull
  @Override
  protected BaseOutputReader.Options readerOptions() {
    return BaseOutputReader.Options.forMostlySilentProcess();
  }

  private void doCloseCommunication() {
    if (myPydevConsoleCommunication != null) {

      UIUtil.invokeAndWaitIfNeeded(() -> {
        try {
          myPydevConsoleCommunication.close();
          Thread.sleep(300);
        }
        catch (Exception e1) {
          // Ignore
        }
      });

      // waiting for REPL communication before destroying process handler
    }
  }

  @NotNull
  public PydevConsoleCommunication getPydevConsoleCommunication() {
    return myPydevConsoleCommunication;
  }
}

