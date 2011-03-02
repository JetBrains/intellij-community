package com.jetbrains.python.console;

import com.intellij.execution.console.LanguageConsoleViewImpl;
import com.intellij.execution.runners.ConsoleExecuteActionHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.jetbrains.python.console.pydev.ConsoleCommunication;
import org.jetbrains.annotations.NotNull;

/**
 * @author oleg
 */
public class PydevLanguageConsoleView extends LanguageConsoleViewImpl {
  private ConsoleExecuteActionHandler myExecuteActionHandler;


  public PydevLanguageConsoleView(final Project project, final String title) {
    super(project, new PydevLanguageConsole(project, title));
  }

  public void setConsoleCommunication(final ConsoleCommunication communication) {
    getPydevLanguageConsole().setConsoleCommunication(communication);
  }

  private PydevLanguageConsole getPydevLanguageConsole() {
    return ((PydevLanguageConsole)myConsole);
  }

  public void setExecutionHandler(@NotNull ConsoleExecuteActionHandler consoleExecuteActionHandler) {
    myExecuteActionHandler = consoleExecuteActionHandler;
  }

  public void executeStatement(@NotNull String statement, @NotNull final Key attributes) {
    PyConsoleHighlightingUtil.processOutput(getConsole(), statement, attributes);
    myExecuteActionHandler.processLine(statement);
  }

  public void executeMultiline(@NotNull String text) {
    getPydevLanguageConsole().addTextRangeToHistory(text);
    myExecuteActionHandler.runExecuteAction(getPydevLanguageConsole());
  }


  public void flushUIUpdates() {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        getConsole().flushAllUiUpdates();
      }
    });
  }
}