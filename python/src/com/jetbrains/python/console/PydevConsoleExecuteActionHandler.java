package com.jetbrains.python.console;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.execution.console.LanguageConsoleImpl;
import com.intellij.execution.console.LanguageConsoleViewImpl;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ConsoleExecuteActionHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.codeStyle.HelperFactory;
import com.intellij.psi.impl.source.codeStyle.IndentHelper;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.console.pydev.ConsoleCommunication;
import com.jetbrains.python.console.pydev.ConsoleCommunicationListener;
import com.jetbrains.python.console.pydev.ICallback;
import com.jetbrains.python.console.pydev.InterpreterResponse;
import org.apache.commons.lang.StringUtils;

import java.util.Scanner;

/**
 * @author traff
 */
public class PydevConsoleExecuteActionHandler extends ConsoleExecuteActionHandler implements ConsoleCommunicationListener {
  private static final String DOUBLE_QUOTE_MULTILINE = "\"\"\"";
  private static final String SINGLE_QUOTE_MULTILINE = "'''";

  private final LanguageConsoleViewImpl myConsoleView;

  private String myInMultilineStringState = null;
  private StringBuilder myInputBuffer;
  private int myCurrentIndentSize = -1;
  private IndentHelper myIndentHelper;

  private final ConsoleCommunication myConsoleCommunication;
  private boolean myEnabled = false;

  public PydevConsoleExecuteActionHandler(LanguageConsoleViewImpl consoleView,
                                          ProcessHandler myProcessHandler,
                                          ConsoleCommunication consoleCommunication) {
    super(myProcessHandler, false);
    myConsoleView = consoleView;
    myConsoleCommunication = consoleCommunication;
    myConsoleCommunication.addCommunicationListener(this);
    myIndentHelper = HelperFactory.createHelper(PythonFileType.INSTANCE, consoleView.getConsole().getProject());
  }

  @Override
  public void processLine(final String text) {
    if ("".equals(text)) {
      processOneLine(text);
    }
    else {
      Scanner s = new Scanner(text);
      while (s.hasNextLine()) {
        String line = s.nextLine();
        processOneLine(line);
      }
    }
  }

  private void processOneLine(String line) {
    int indentSize = myIndentHelper.getIndent(line, false);
    if (indentSize == 0 && indentSize < myCurrentIndentSize && !shouldIndent(line)) {
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
    if (myConsoleCommunication != null && myConsoleCommunication.isWaitingForInput()) {
      myInputBuffer.setLength(0);
    }
    myInputBuffer.append(line).append("\n");

    // multiline strings handling
    if (myInMultilineStringState != null) {
      if (isMultilineStarts(line, DOUBLE_QUOTE_MULTILINE)) {
        myInMultilineStringState = null;
        // restore language
        console.setLanguage(PythonLanguage.getInstance());
        console.setPrompt(PyConsoleHighlightingUtil.ORDINARY_PROMPT);
      }
      else {
        return;
      }
    }
    else {
      if (isMultilineStarts(line, DOUBLE_QUOTE_MULTILINE)) {
        myInMultilineStringState = DOUBLE_QUOTE_MULTILINE;
      }
      else if (isMultilineStarts(line, SINGLE_QUOTE_MULTILINE)) {
        myInMultilineStringState = SINGLE_QUOTE_MULTILINE;
      }
      if (myInMultilineStringState != null) {
        // change language
        console.setLanguage(PlainTextLanguage.INSTANCE);
        console.setPrompt(PyConsoleHighlightingUtil.INDENT_PROMPT);
        return;
      }
    }

    // Process line continuation
    if (line.endsWith("\\")) {
      console.setPrompt(PyConsoleHighlightingUtil.INDENT_PROMPT);
      return;
    }

    if (!StringUtil.isEmptyOrSpaces(line)) {
      int indent = myIndentHelper.getIndent(line, false);
      boolean flag = false;
      if (shouldIndent(line)) {
        indent += getPythonIndent();
        flag = true;
      }
      if ((myCurrentIndentSize != -1 && indent >= myCurrentIndentSize) || flag) {
        myCurrentIndentSize = indent;
        indentEditor(currentEditor, indent);
        more(console, currentEditor);

        return;
      }
    }


    if (myConsoleCommunication != null) {
      final boolean waitedForInputBefore = myConsoleCommunication.isWaitingForInput();
      executingPrompt(console);
      myConsoleCommunication.execInterpreter(myInputBuffer.toString(), new ICallback<Object, InterpreterResponse>() {
        public Object call(final InterpreterResponse interpreterResponse) {
          // clear
          myInputBuffer = null;
          // Handle prompt
          if (interpreterResponse.need_input) {
            if (!PyConsoleHighlightingUtil.INPUT_PROMPT.equals(console.getPrompt())) {
              console.setPrompt(PyConsoleHighlightingUtil.INPUT_PROMPT);
              scrollDown(currentEditor);
            }
            myCurrentIndentSize = -1;
          }
          else if (interpreterResponse.more) {
            more(console, currentEditor);
            if (myCurrentIndentSize == -1) {
              // compute current indentation
              myCurrentIndentSize = myIndentHelper.getIndent(line, false) + getPythonIndent();
              // In this case we can insert indent automatically
              indentEditor(currentEditor, myCurrentIndentSize);
            }
          }
          else {
            ordinaryPrompt(console, currentEditor);
            myCurrentIndentSize = -1;
          }

          return null;
        }
      });
      // After requesting input we got no call back to change prompt, change it manually
      if (waitedForInputBefore && !myConsoleCommunication.isWaitingForInput()) {
        console.setPrompt(PyConsoleHighlightingUtil.ORDINARY_PROMPT);
        scrollDown(currentEditor);
      }
    }
  }

  private void ordinaryPrompt(LanguageConsoleImpl console, Editor currentEditor) {
    if (!myConsoleCommunication.isExecuting()) {
      if (!PyConsoleHighlightingUtil.ORDINARY_PROMPT.equals(console.getPrompt())) {
        console.setPrompt(PyConsoleHighlightingUtil.ORDINARY_PROMPT);
        scrollDown(currentEditor);
      }
    }
    else {
      executingPrompt(console);
    }
  }

  private void executingPrompt(LanguageConsoleImpl console) {
    console.setPrompt(PyConsoleHighlightingUtil.EXECUTING_PROMPT);
  }

  private void more(LanguageConsoleImpl console, Editor currentEditor) {
    if (!PyConsoleHighlightingUtil.INDENT_PROMPT.equals(console.getPrompt())) {
      console.setPrompt(PyConsoleHighlightingUtil.INDENT_PROMPT);
      scrollDown(currentEditor);
    }
  }

  public String getPrevCommandRunningMessage() {
    return "Previous command is still running. Wait for it termination or press Ctrl+C to interrupt.";
  }

  @Override
  public void executionFinished() {
    final LanguageConsoleImpl console = myConsoleView.getConsole();
    final Editor currentEditor = console.getCurrentEditor();
    ordinaryPrompt(console, currentEditor);
  }

  private static boolean isMultilineStarts(String line, String substring) {
    return StringUtils.countMatches(line, substring) % 2 == 1;
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
        EditorModificationUtil.insertStringAtCaret(editor, myIndentHelper.fillIndent(indentSize));
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

  private static void scrollDown(final Editor currentEditor) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        currentEditor.getCaretModel().moveToOffset(currentEditor.getDocument().getTextLength());
      }
    });
  }

  @Override
  public void runExecuteAction(LanguageConsoleImpl languageConsole) {
    if (isEnabled()) {
      if (myConsoleCommunication.isExecuting()) {
        HintManager.getInstance().showErrorHint(languageConsole.getConsoleEditor(), getPrevCommandRunningMessage());
      }
      else {
        super.runExecuteAction(languageConsole);
      }
    }
    else {
      HintManager.getInstance().showErrorHint(languageConsole.getConsoleEditor(), getConsoleIsNotEnabledMessage());
    }
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
