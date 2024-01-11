// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.commandInterfaceConsole;

import com.intellij.execution.actions.StopProcessAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.NotNull;

/**
 * "Stop" action that stops process, attached to console.
 * It also stops process when {@link #run()} is called. Useful for cases like some "close listener"
 *
 * @author Ilya.Kazakevich
 */
final class ConsoleStopProcessAction extends StopProcessAction implements Runnable {
  private final ConsoleWithProcess myConsole;


  ConsoleStopProcessAction(@NotNull final ConsoleWithProcess console) {
    super(PyBundle.message("windowWithActions.stopProcess"), null, null);
    myConsole = console;
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    super.update(e);
    setProcessHandler(myConsole.getProcessHandler()); // Attach action to process handler (if any) or detach (if no process runs)
  }


  @Override
  public void run() {
    stopProcess(myConsole.getProcessHandler());
  }
}
