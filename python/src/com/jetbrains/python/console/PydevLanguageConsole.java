package com.jetbrains.python.console;

import com.intellij.execution.console.LanguageConsoleImpl;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.console.pydev.PydevConsoleCommunication;

/**
 * @author oleg
 */
public class PydevLanguageConsole extends LanguageConsoleImpl {
  public PydevLanguageConsole(final Project project, final String title) {
    super(project, title, PythonLanguage.getInstance(), false);
  }

  public void setPydevConsoleCommunication(final PydevConsoleCommunication communication) {
    myFile.putCopyableUserData(PydevConsoleRunner.CONSOLE_KEY, communication);
  }
}