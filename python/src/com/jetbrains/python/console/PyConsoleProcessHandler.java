package com.jetbrains.python.console;

import com.intellij.execution.console.LanguageConsoleImpl;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.console.pydev.PydevConsoleCommunication;
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
  protected void textAvailable(final String text, final Key attributes) {
    final String string = PyPromptUtil.processPrompts(getConsole(), StringUtil.convertLineSeparators(text));

    myConsoleView.printText(string, attributes);
  }

  @Override
  protected void destroyProcessImpl() {
    doCloseCommunication();
    super.destroyProcessImpl();
  }

  @Override
  protected void detachProcessImpl() {
    doCloseCommunication();
    super.detachProcessImpl();
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

