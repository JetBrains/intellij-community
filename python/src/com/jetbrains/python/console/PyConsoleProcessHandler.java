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
package com.jetbrains.python.console;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.run.PythonProcessHandler;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;

/**
 * @author traff
 */
public class PyConsoleProcessHandler extends PythonProcessHandler {
  private final PythonConsoleView myConsoleView;
  private final PydevConsoleCommunication myPydevConsoleCommunication;

  public PyConsoleProcessHandler(final Process process,
                                 PythonConsoleView consoleView,
                                 PydevConsoleCommunication pydevConsoleCommunication,
                                 @NotNull String commandLine,
                                 final Charset charset) {
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
  }

  @Override
  public void coloredTextAvailable(final String text, final Key attributes) {
    String string = PyConsoleUtil.processPrompts(myConsoleView, StringUtil.convertLineSeparators(text));

    myConsoleView.print(string, attributes);

    notifyColoredListeners(text, attributes);
  }

  @Override
  protected void closeStreams() {
    doCloseCommunication();
    super.closeStreams();
  }

  @Override
  public boolean isSilentlyDestroyOnClose() {
    return !myPydevConsoleCommunication.isExecuting();
  }

  @Override
  protected boolean shouldKillProcessSoftly() {
    return false;
  }

  private void doCloseCommunication() {
    if (myPydevConsoleCommunication != null) {

      UIUtil.invokeAndWaitIfNeeded(new Runnable() {
        @Override
        public void run() {
          try {
            myPydevConsoleCommunication.close();
            Thread.sleep(300);
          }
          catch (Exception e1) {
            // Ignore
          }
        }
      });

      // waiting for REPL communication before destroying process handler
    }
  }
}

