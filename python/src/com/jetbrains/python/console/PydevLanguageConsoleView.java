package com.jetbrains.python.console;

import com.intellij.execution.console.LanguageConsoleViewImpl;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.console.pydev.PydevConsoleCommunication;

/**
 * @author oleg
 */
public class PydevLanguageConsoleView extends LanguageConsoleViewImpl {

  public PydevLanguageConsoleView(final Project project, final String title) {
    super(project, new PydevLanguageConsole(project, title));
  }

  public void setPydevConsoleCommunication(final PydevConsoleCommunication communication){
    ((PydevLanguageConsole)myConsole).setPydevConsoleCommunication(communication);
  }
}