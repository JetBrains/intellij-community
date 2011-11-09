package com.jetbrains.python.console;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.execution.console.LanguageConsoleImpl;
import com.intellij.execution.console.LanguageConsoleViewImpl;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ConsoleExecuteActionHandler;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.codeStyle.IndentHelperImpl;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.console.pydev.ConsoleCommunication;
import com.jetbrains.python.console.pydev.ConsoleCommunicationListener;
import com.jetbrains.python.console.pydev.ICallback;
import com.jetbrains.python.console.pydev.InterpreterResponse;

import java.util.Scanner;

/**
 * @author traff
 */
public class PydevConsoleExecuteActionHandler extends ConsoleExecuteActionHandler implements ConsoleCommunicationListener {

  private final LanguageConsoleViewImpl myConsoleView;

  private String myInMultilineStringState = null;
  private StringBuilder myInputBuffer;
  private int myCurrentIndentSize = -1;

  private final ConsoleCommunication myConsoleCommunication;
  private boolean myEnabled = false;

  public PydevConsoleExecuteActionHandler(LanguageConsoleViewImpl consoleView,
                                          ProcessHandler myProcessHandler,
                                          ConsoleCommunication consoleCommunication) {
    super(myProcessHandler, false);
    myConsoleView = consoleView;
    myConsoleCommunication = consoleCommunication;
    myConsoleCommunication.addCommunicationListener(this);
  }

  @Override
  public void processLine(final String text) {
    processLine(text, false);
  }

  public void processLine(final String text, boolean execAnyway) {
    if (text.isEmpty()) {
      processOneLine(text);
    }
    else {
      Scanner s = new Scanner(text);
      while (s.hasNextLine()) {
        String line = s.nextLine();
        processOneLine(line);
      }
    }
    if (execAnyway && myCurrentIndentSize>0) {
      finishExecution();
    }
  }

  private void processOneLine(String line) {
    int indentSize = IndentHelperImpl.getIndent(getProject(), PythonFileType.INSTANCE, line, false);
    if (StringUtil.isEmptyOrSpaces(line)) {
      doProcessLine("\n");
    }
    else if (indentSize == 0 && indentSize < myCurrentIndentSize && !shouldIndent(line)) {
      doProcessLine("\n");
      doProcessLine(line);
    }
    else {
      doProcessLine(line);
    }
  }

  public void doProcessLine(final String line) {
    final LanguageConsoleImpl console = myConsoleView.getConsole();
    final Editor currentEditor = console.getCurrentEditor();

    if (myInputBuffer == null) {
      myInputBuffer = new StringBuilder();
    }

    if (!StringUtil.isEmptyOrSpaces(line)) {
      myInputBuffer.append(line);
      if (!line.endsWith("\n")) {
        myInputBuffer.append("\n");
      }
    }

    if (StringUtil.isEmptyOrSpaces(line) && StringUtil.isEmptyOrSpaces(myInputBuffer.toString())) {
      myInputBuffer.append("");
    }

    // multiline strings handling
    if (myInMultilineStringState != null) {
      if (PyConsoleUtil.isDoubleQuoteMultilineStarts(line)) {
        myInMultilineStringState = null;
        // restore language
        console.setLanguage(PythonLanguage.getInstance());
        console.setPrompt(PyConsoleUtil.ORDINARY_PROMPT);
      }
      else {
        return;
      }
    }
    else {
      if (PyConsoleUtil.isDoubleQuoteMultilineStarts(line)) {
        myInMultilineStringState = PyConsoleUtil.DOUBLE_QUOTE_MULTILINE;
      }
      else if (PyConsoleUtil.isSingleQuoteMultilineStarts(line)) {
        myInMultilineStringState = PyConsoleUtil.SINGLE_QUOTE_MULTILINE;
      }
      if (myInMultilineStringState != null) {
        // change language
        console.setLanguage(PlainTextLanguage.INSTANCE);
        console.setPrompt(PyConsoleUtil.INDENT_PROMPT);
        return;
      }
    }

    // Process line continuation
    if (line.endsWith("\\")) {
      console.setPrompt(PyConsoleUtil.INDENT_PROMPT);
      return;
    }

    if (!StringUtil.isEmptyOrSpaces(line)) {
      int indent = IndentHelperImpl.getIndent(getProject(), PythonFileType.INSTANCE, line, false);
      boolean flag = false;
      if (shouldIndent(line)) {
        indent += getPythonIndent();
        flag = true;
      }
      if ((myCurrentIndentSize > 0 && indent > 0) || flag) {
        myCurrentIndentSize = indent;
        indentEditor(currentEditor, indent);
        more(console, currentEditor);

        return;
      }
    }


    if (myConsoleCommunication != null) {
      final boolean waitedForInputBefore = myConsoleCommunication.isWaitingForInput();
      final String command = myInputBuffer.toString();
      if (myConsoleCommunication.isWaitingForInput()) {
        myInputBuffer.setLength(0);
      }
      else {
        executingPrompt(console);
      }
      myConsoleCommunication.execInterpreter(command, new ICallback<Object, InterpreterResponse>() {
        public Object call(final InterpreterResponse interpreterResponse) {
          // clear
          myInputBuffer = null;
          // Handle prompt
          if (interpreterResponse.need_input) {
            if (!PyConsoleUtil.INPUT_PROMPT.equals(console.getPrompt()) && !PyConsoleUtil.HELP_PROMPT.equals(console.getPrompt())) {
              console.setPrompt(PyConsoleUtil.INPUT_PROMPT);
              PyConsoleUtil.scrollDown(currentEditor);
            }
            myCurrentIndentSize = -1;
          }
          else if (interpreterResponse.more) {
            more(console, currentEditor);
            if (myCurrentIndentSize == -1) {
              // compute current indentation
              myCurrentIndentSize = IndentHelperImpl.getIndent(getProject(), PythonFileType.INSTANCE, line, false) + getPythonIndent();
              // In this case we can insert indent automatically
              indentEditor(currentEditor, myCurrentIndentSize);
            }
          }
          else {
            if (!myConsoleCommunication.isWaitingForInput()) {
              ordinaryPrompt(console, currentEditor);
            }
            myCurrentIndentSize = -1;
          }

          return null;
        }
      });
      // After requesting input we got no call back to change prompt, change it manually
      if (waitedForInputBefore && !myConsoleCommunication.isWaitingForInput()) {
        console.setPrompt(PyConsoleUtil.ORDINARY_PROMPT);
        PyConsoleUtil.scrollDown(currentEditor);
      }
    }
  }

  private void ordinaryPrompt(LanguageConsoleImpl console, Editor currentEditor) {
    if (!myConsoleCommunication.isExecuting()) {
      if (!PyConsoleUtil.ORDINARY_PROMPT.equals(console.getPrompt())) {
        console.setPrompt(PyConsoleUtil.ORDINARY_PROMPT);
        PyConsoleUtil.scrollDown(currentEditor);
      }
    }
    else {
      executingPrompt(console);
    }
  }

  private static void executingPrompt(LanguageConsoleImpl console) {
    console.setPrompt(PyConsoleUtil.EXECUTING_PROMPT);
  }

  private void more(LanguageConsoleImpl console, Editor currentEditor) {
    if (!PyConsoleUtil.INDENT_PROMPT.equals(console.getPrompt())) {
      console.setPrompt(PyConsoleUtil.INDENT_PROMPT);
      PyConsoleUtil.scrollDown(currentEditor);
    }
  }

  public static String getPrevCommandRunningMessage() {
    return "Previous command is still running. Please wait or press Ctrl+C to interrupt.";
  }

  @Override
  public void executionFinished() {
    final LanguageConsoleImpl console = myConsoleView.getConsole();
    final Editor currentEditor = console.getCurrentEditor();
    ordinaryPrompt(console, currentEditor);
  }

  @Override
  public void finishExecution() {
    final LanguageConsoleImpl console = myConsoleView.getConsole();
    final Editor currentEditor = console.getCurrentEditor();

    if (myInputBuffer != null) {
      processLine("\n");
    }

    cleanEditor(currentEditor);
    //console.setPrompt(PyConsoleHighlightingUtil.ORDINARY_PROMPT);
  }

  public int getCurrentIndentSize() {
    return myCurrentIndentSize;
  }

  private boolean shouldIndent(String line) {
    return line.endsWith(":");
  }

  public int getPythonIndent() {
    return CodeStyleSettingsManager.getSettings(getProject()).getIndentSize(PythonFileType.INSTANCE);
  }

  private void indentEditor(final Editor editor, final int indentSize) {
    new WriteCommandAction(getProject()) {
      @Override
      protected void run(Result result) throws Throwable {
        EditorModificationUtil.insertStringAtCaret(editor, IndentHelperImpl.fillIndent(getProject(), PythonFileType.INSTANCE, indentSize));
      }
    }.execute();
  }

  private void cleanEditor(final Editor editor) {
    new WriteCommandAction(getProject()) {
      @Override
      protected void run(Result result) throws Throwable {
        editor.getDocument().setText("");
      }
    }.execute();
  }

  private Project getProject() {
    return myConsoleView.getConsole().getProject();
  }

  @Override
  public void runExecuteAction(LanguageConsoleImpl languageConsole) {
    if (isEnabled()) {
      if (!canExecuteNow()) {
        HintManager.getInstance().showErrorHint(languageConsole.getConsoleEditor(), getPrevCommandRunningMessage());
      }
      else {
        doRunExecuteAction(languageConsole);
      }
    }
    else {
      HintManager.getInstance().showErrorHint(languageConsole.getConsoleEditor(), getConsoleIsNotEnabledMessage());
    }
  }

  private void doRunExecuteAction(LanguageConsoleImpl languageConsole) {
    if (shouldCopyToHistory(languageConsole)) {
      copyToHistoryAndExecute(languageConsole);
    }
    else {
      final Document document = languageConsole.getCurrentEditor().getDocument();
      processLine(document.getText());
    }
  }

  private static boolean shouldCopyToHistory(LanguageConsoleImpl console) {
    return !PyConsoleUtil.isPagingPrompt(console.getPrompt());
  }

  private void copyToHistoryAndExecute(LanguageConsoleImpl languageConsole) {
    super.runExecuteAction(languageConsole);
  }

  public boolean canExecuteNow() {
    return !myConsoleCommunication.isExecuting() || myConsoleCommunication.isWaitingForInput();
  }

  protected String getConsoleIsNotEnabledMessage() {
    return "Console is not enabled.";
  }

  protected void setEnabled(boolean flag) {
    myEnabled = flag;
  }

  public boolean isEnabled() {
    return myEnabled;
  }
}
