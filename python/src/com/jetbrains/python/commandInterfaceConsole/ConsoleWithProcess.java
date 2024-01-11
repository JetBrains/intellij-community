// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.commandInterfaceConsole;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import org.jetbrains.annotations.Nullable;

// TODO: Move to the same package as ConsoleView
/**
 * Console that knows how to run process. Such console stores somewhere {@link ProcessHandler} passed
 * to {@link #attachToProcess(ProcessHandler)} and may return it via {@link #getProcessHandler()}
 *
 * @author Ilya.Kazakevich
 */
interface ConsoleWithProcess extends ConsoleView {
  /**
   * @return process handler of process currently running or null if no such process
   */
  @Nullable
  ProcessHandler getProcessHandler();
}
