package com.jetbrains.python.console;

import com.intellij.execution.console.LanguageConsoleViewImpl;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.console.pydev.ConsoleCommunication;

/**
 * @author oleg
 */
public class PydevLanguageConsoleView extends LanguageConsoleViewImpl {

  public PydevLanguageConsoleView(final Project project, final String title) {
    super(project, new PydevLanguageConsole(project, title));
  }

  public void setConsoleCommunication(final ConsoleCommunication communication){
    ((PydevLanguageConsole)myConsole).setConsoleCommunication(communication);
  }
}