/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.console;

import com.intellij.execution.console.LanguageConsoleView;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Key;
import com.jetbrains.python.console.pydev.ConsoleCommunication;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public interface PydevConsoleRunner {
  Key<ConsoleCommunication> CONSOLE_COMMUNICATION_KEY = new Key<>("PYDEV_CONSOLE_COMMUNICATION_KEY");
  Key<Sdk> CONSOLE_SDK = new Key<>("PYDEV_CONSOLE_SDK_KEY");

  interface ConsoleListener {
    void handleConsoleInitialized(@NotNull LanguageConsoleView consoleView);
  }

  void open();

  void runSync(boolean requestEditorFocus);

  void run(boolean requestEditorFocus);

  void reRun(boolean requestEditorFocus, String title);

  PydevConsoleCommunication getPydevConsoleCommunication();

  void addConsoleListener(PydevConsoleRunnerImpl.ConsoleListener consoleListener);

  PythonConsoleExecuteActionHandler getConsoleExecuteActionHandler();

  ProcessHandler getProcessHandler();

  PythonConsoleView getConsoleView();

  @Nullable
  default AnAction createRerunAction() { return null; }

  @TestOnly
  void setSdk(Sdk sdk);

  @TestOnly
  @Nullable Sdk getSdk();
}
