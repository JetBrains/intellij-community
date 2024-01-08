// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.commandInterfaceConsole;

import com.intellij.execution.console.ProcessBackedConsoleExecuteActionHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

/**
 * Supports {@link CommandConsole} in "process-mode"
 * Delegates console streams to process
 *
 * @author Ilya.Kazakevich
 */
final class ProcessModeConsumer implements Consumer<String> {
  @NotNull
  private final ProcessBackedConsoleExecuteActionHandler myHandler;

  ProcessModeConsumer(@NotNull final ProcessHandler processHandler) {
    myHandler = new ProcessBackedConsoleExecuteActionHandler(processHandler, true);
  }

  @Override
  public void consume(final String t) {
    myHandler.processLine(t);
  }
}
