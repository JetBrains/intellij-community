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
package com.jetbrains.python.console;

import com.intellij.execution.process.*;
import com.intellij.openapi.util.Key;

import java.io.OutputStream;

/**
 * @author traff
 */
public class PyConsoleDebugProcessHandler extends ProcessHandler {
  private PydevConsoleCommunication myConsoleCommunication;

  public PyConsoleDebugProcessHandler(final PyConsoleProcessHandler processHandler, PydevConsoleCommunication communication) {
    myConsoleCommunication = communication;
    processHandler.addProcessListener(new ProcessListener() {
      @Override
      public void startNotified(ProcessEvent event) {

      }

      @Override
      public void processTerminated(ProcessEvent event) {
        PyConsoleDebugProcessHandler.this.notifyProcessTerminated(event.getExitCode());
      }

      @Override
      public void processWillTerminate(ProcessEvent event, boolean willBeDestroyed) {

      }

      @Override
      public void onTextAvailable(ProcessEvent event, Key outputType) {
      }
    });

    processHandler.addColoredTextListener(new AnsiEscapeDecoder.ColoredTextAcceptor() {
      @Override
      public void coloredTextAvailable(String text, Key attributes) {
        PyConsoleDebugProcessHandler.this.notifyTextAvailable(text, attributes);
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
}
