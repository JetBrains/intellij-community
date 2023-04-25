// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.console;

import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.io.BaseOutputReader;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.run.PythonProcessHandler;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;

/**
 * The class is obsolete. Use an original instance of {@code com.intellij.execution.process.ProcessHandler} and configure it using
 * {@link PyConsoleProcessHandlers#configureProcessHandlerForPythonConsole(ProcessHandler, PythonConsoleView, PydevConsoleCommunication)}.
 * <p>
 * The class is going to be deprecated and then removed when the flag {@code python.use.targets.api} is eliminated.
 */
@ApiStatus.Obsolete
public class PyConsoleProcessHandler extends PythonProcessHandler {
  private final PydevConsoleCommunication myPydevConsoleCommunication;

  public PyConsoleProcessHandler(final Process process,
                                 @NotNull PythonConsoleView consoleView,
                                 @NotNull PydevConsoleCommunication pydevConsoleCommunication,
                                 @NotNull String commandLine,
                                 @NotNull Charset charset) {
    super(process, commandLine, charset);
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

