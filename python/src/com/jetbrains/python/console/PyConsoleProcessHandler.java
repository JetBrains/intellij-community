/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.execution.console.LanguageConsoleImpl;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.run.PythonProcessHandler;

import java.nio.charset.Charset;

/**
 * @author oleg
 */
public class PyConsoleProcessHandler extends PythonProcessHandler {
  private final PythonConsoleView myConsoleView;
  private final PydevConsoleCommunication myPydevConsoleCommunication;

  public PyConsoleProcessHandler(final Process process,
                                 PythonConsoleView consoleView,
                                 PydevConsoleCommunication pydevConsoleCommunication, final String commandLine,
                                 final Charset charset) {
    super(process, commandLine, charset);
    myConsoleView = consoleView;
    myPydevConsoleCommunication = pydevConsoleCommunication;
  }

  @Override
  public void coloredTextAvailable(final String text, final Key attributes) {
    final String string = PyConsoleUtil.processPrompts(getConsole(), StringUtil.convertLineSeparators(text));

    myConsoleView.print(string, attributes);
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

  private LanguageConsoleImpl getConsole() {
    return myConsoleView.getConsole();
  }
}

