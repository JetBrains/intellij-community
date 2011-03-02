package com.jetbrains.python.console;

import com.intellij.execution.console.LanguageConsoleImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.console.pydev.ConsoleCommunication;

/**
 * @author oleg
 */
public class PydevLanguageConsole extends LanguageConsoleImpl {
  public PydevLanguageConsole(final Project project, final String title) {
    super(project, title, PythonLanguage.getInstance());
    // Mark editor as console one, to prevent autopopup completion
    getConsoleEditor().putUserData(PydevCompletionAutopopupBlockingHandler.REPL_KEY, new Object());
  }

  public void setConsoleCommunication(final ConsoleCommunication communication) {
    myFile.putCopyableUserData(PydevConsoleRunner.CONSOLE_KEY, communication);
  }
}