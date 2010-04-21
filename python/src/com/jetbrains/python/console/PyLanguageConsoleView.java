package com.jetbrains.python.console;

import com.intellij.execution.console.LanguageConsoleViewImpl;
import com.intellij.openapi.project.Project;

/**
 * @author oleg
 */
public class PyLanguageConsoleView extends LanguageConsoleViewImpl {

  public PyLanguageConsoleView(final Project project, String title) {
    super(project, new PyLanguageConsole(project, title));
  }

  public void inputSent(final String text){
    ((PyLanguageConsole) getConsole()).inputSent(text);
  }
}
