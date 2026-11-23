// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.console;

import com.intellij.execution.console.LanguageConsoleView;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.util.Key;
import com.intellij.python.sdk.backend.PythonInterpreter;
import com.jetbrains.python.console.pydev.ConsoleCommunication;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public interface PydevConsoleRunner {
  Key<ConsoleCommunication> CONSOLE_COMMUNICATION_KEY = new Key<>("PYDEV_CONSOLE_COMMUNICATION_KEY");
  /** The interpreter of the console that a console file belongs to. */
  Key<PythonInterpreter> CONSOLE_INTERPRETER = new Key<>("PYDEV_CONSOLE_INTERPRETER_KEY");

  interface ConsoleListener {
    void handleConsoleInitialized(@NotNull LanguageConsoleView consoleView);
  }

  void open();

  void runSync(boolean requestEditorFocus);

  void run(boolean requestEditorFocus);

  void reRun(boolean requestEditorFocus, String title);

  PydevConsoleCommunication getPydevConsoleCommunication();

  void addConsoleListener(PydevConsoleRunnerImpl.ConsoleListener consoleListener);

  @ApiStatus.Internal
  void removeConsoleListener(PydevConsoleRunnerImpl.ConsoleListener consoleListener);

  PythonConsoleExecuteActionHandler getConsoleExecuteActionHandler();

  ProcessHandler getProcessHandler();

  PythonConsoleView getConsoleView();

  default @Nullable AnAction createRerunAction() { return null; }

  @TestOnly
  @ApiStatus.Internal
  void setPythonInterpreter(@Nullable PythonInterpreter interpreter);

  /** The interpreter that this console runs. */
  @ApiStatus.Internal
  @Nullable PythonInterpreter getPythonInterpreter();

}
