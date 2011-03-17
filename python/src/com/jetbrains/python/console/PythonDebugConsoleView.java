package com.jetbrains.python.console;

import com.intellij.execution.console.LanguageConsoleImpl;
import com.intellij.execution.console.LanguageConsoleViewImpl;
import com.intellij.execution.runners.ConsoleExecuteActionHandler;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.console.pydev.ConsoleCommunication;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class PythonDebugConsoleView extends LanguageConsoleViewImpl implements PyCodeExecutor {
  private ConsoleExecuteActionHandler myExecuteActionHandler;

  public PythonDebugConsoleView(final Project project, final String title) {
    super(project, new PythonLanguageConsole(project, title));
    getPythonLanguageConsole().setPythonDebugConsoleView(this);
    getPythonLanguageConsole().setPrompt(PyConsoleHighlightingUtil.ORDINARY_PROMPT);
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
  }

  private static class PythonLanguageConsole extends LanguageConsoleImpl {
    private PythonDebugConsoleView myPythonDebugConsoleView;

    public PythonLanguageConsole(final Project project, final String title) {
      super(project, title, PythonLanguage.getInstance());
      // Mark editor as console one, to prevent autopopup completion
      getConsoleEditor().putUserData(PydevCompletionAutopopupBlockingHandler.REPL_KEY, new Object());
    }

    public void setPythonDebugConsoleView(PythonDebugConsoleView pythonDebugConsoleView) {
      myPythonDebugConsoleView = pythonDebugConsoleView;
    }

    public void setConsoleCommunication(final ConsoleCommunication communication) {
      myFile.putCopyableUserData(PydevConsoleRunner.CONSOLE_KEY, communication);
    }

    @Override
    protected void appendToHistoryDocument(@NotNull Document history, @NotNull String text) {
      myPythonDebugConsoleView.beforeExternalAddContentToDocument(text.length(), ConsoleViewContentType.NORMAL_OUTPUT);
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
