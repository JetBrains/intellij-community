/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.toolWindowWithActions;

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
  public void update(final AnActionEvent e) {
    super.update(e);
    setProcessHandler(myConsole.getProcessHandler()); // Attach action to process handler (if any) or detach (if no process runs)
  }


  @Override
  public void run() {
    stopProcess(myConsole.getProcessHandler());
  }
}
