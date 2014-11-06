/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.debugger;

import com.intellij.execution.console.LanguageConsoleView;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.xdebugger.XDebugSessionListener;
import com.jetbrains.python.console.PydevConsoleExecuteActionHandler;
import com.jetbrains.python.console.pydev.ConsoleCommunication;

/**
 * @author traff
 */
public class PydevDebugConsoleExecuteActionHandler extends PydevConsoleExecuteActionHandler implements XDebugSessionListener {

  public PydevDebugConsoleExecuteActionHandler(LanguageConsoleView consoleView,
                                               ProcessHandler myProcessHandler,
                                               ConsoleCommunication consoleCommunication) {
    super(consoleView, myProcessHandler, consoleCommunication);
  }

  @Override
  protected String getConsoleIsNotEnabledMessage() {
    return "Pause the process to use command-line.";
  }

  @Override
  public void sessionPaused() {
    setEnabled(true);
  }

  @Override
  public void sessionResumed() {
    setEnabled(false);
  }

  @Override
  public void sessionStopped() {
    setEnabled(false);
  }

  @Override
  public void stackFrameChanged() {
  }

  @Override
  public void beforeSessionResume() {
  }
}
