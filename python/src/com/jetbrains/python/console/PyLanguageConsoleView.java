package com.jetbrains.python.console;

import com.intellij.execution.console.LanguageConsoleImpl;
import com.intellij.execution.console.LanguageConsoleViewImpl;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NotNullLazyValue;
import org.jetbrains.annotations.NotNull;

/**
 * @author oleg
 */
public class PyLanguageConsoleView extends LanguageConsoleViewImpl implements ConsoleNotification {

  public PyLanguageConsoleView(final Project project, String title) {
    super(project, new PyLanguageConsole(project, title));
  }

  public void inputSent(final String text){
    ((PyLanguageConsole) getConsole()).inputSent(text);
  }
}
