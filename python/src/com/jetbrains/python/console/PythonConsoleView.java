package com.jetbrains.python.console;

import com.intellij.execution.console.LanguageConsoleImpl;
import com.intellij.execution.console.LanguageConsoleViewImpl;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.ConsoleExecuteActionHandler;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.IdeFocusManager;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.console.completion.PythonConsoleAutopopupBlockingHandler;
import com.jetbrains.python.console.pydev.ConsoleCommunication;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class PythonConsoleView extends LanguageConsoleViewImpl implements PyCodeExecutor {
  private ConsoleExecuteActionHandler myExecuteActionHandler;

  public PythonConsoleView(final Project project, final String title) {
    super(project, new PythonLanguageConsole(project, title));
    getPythonLanguageConsole().setPythonConsoleView(this);
    getPythonLanguageConsole().setPrompt(PyPromptUtil.ORDINARY_PROMPT);
  }

  public void setConsoleCommunication(final ConsoleCommunication communication) {
    getPythonLanguageConsole().setConsoleCommunication(communication);
  }

  public void setExecutionHandler(@NotNull ConsoleExecuteActionHandler consoleExecuteActionHandler) {
    myExecuteActionHandler = consoleExecuteActionHandler;
  }

  public void requestFocus() {
    IdeFocusManager.findInstance().requestFocus(getPythonLanguageConsole().getConsoleEditor().getContentComponent(), true);
  }

  private PythonLanguageConsole getPythonLanguageConsole() {
    return ((PythonLanguageConsole)myConsole);
  }

  @Override
  public void executeCode(@NotNull String code) {
    getPythonLanguageConsole().addTextToCurrentEditor(PyConsoleIndentUtil.normalize(code));
    myExecuteActionHandler.runExecuteAction(getPythonLanguageConsole());
    myExecuteActionHandler.finishExecution();
  }

  public void executeStatement(@NotNull String statement, @NotNull final Key attributes) {
    printText(statement, attributes);
    myExecuteActionHandler.processLine(statement);
  }

  public void printText(String text, final Key attributes) {
    print(text, outputTypeForAttributes(attributes));
  }

  public static ConsoleViewContentType outputTypeForAttributes(Key attributes) {
    final ConsoleViewContentType outputType;
    if (attributes == ProcessOutputTypes.STDERR) {
      outputType = ConsoleViewContentType.ERROR_OUTPUT;
    } else
    if (attributes == ProcessOutputTypes.SYSTEM) {
      outputType = ConsoleViewContentType.SYSTEM_OUTPUT;
    } else {
      outputType = ConsoleViewContentType.NORMAL_OUTPUT;
    }
    return outputType;
  }

  private static class PythonLanguageConsole extends LanguageConsoleImpl {
    private PythonConsoleView myPythonConsoleView;

    public PythonLanguageConsole(final Project project, final String title) {
      super(project, title, PythonLanguage.getInstance());
      // Mark editor as console one, to prevent autopopup completion
      getConsoleEditor().putUserData(PythonConsoleAutopopupBlockingHandler.REPL_KEY, new Object());
    }

    public void setPythonConsoleView(PythonConsoleView pythonConsoleView) {
      myPythonConsoleView = pythonConsoleView;
    }

    public void setConsoleCommunication(final ConsoleCommunication communication) {
      myFile.putCopyableUserData(PydevConsoleRunner.CONSOLE_KEY, communication);
    }

    @Override
    protected void appendToHistoryDocument(@NotNull Document history, @NotNull String text) {
      myPythonConsoleView.beforeExternalAddContentToDocument(text.length(), ConsoleViewContentType.NORMAL_OUTPUT);
      super.appendToHistoryDocument(history, text);
    }
  }

  @Override
  protected EditorEx createRealEditor() {
    EditorEx editor = myConsole.getHistoryViewer();
    editor.setHighlighter(createHighlighter());
    return editor;
  }
}
