package com.jetbrains.python.debugger;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.execution.console.LanguageConsoleImpl;
import com.intellij.execution.console.LanguageConsoleViewImpl;
import com.intellij.execution.process.ConsoleHistoryModel;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.xdebugger.XDebugSessionListener;
import com.jetbrains.python.console.PydevConsoleExecuteActionHandler;
import com.jetbrains.python.console.pydev.ConsoleCommunication;

/**
 * @author traff
 */
public class PydevDebugConsoleExecuteActionHandler extends PydevConsoleExecuteActionHandler implements XDebugSessionListener {
  private boolean myEnabled = false;

  public PydevDebugConsoleExecuteActionHandler(LanguageConsoleViewImpl consoleView,
                                               ProcessHandler myProcessHandler,
                                               ConsoleCommunication consoleCommunication) {
    super(consoleView, myProcessHandler, consoleCommunication);
  }

  public void runExecuteAction(LanguageConsoleImpl languageConsole,
                                  ConsoleHistoryModel consoleHistoryModel) {
    if (isEnabled()) {
      super.runExecuteAction(languageConsole, consoleHistoryModel);
    } else {
      HintManager.getInstance().showErrorHint(languageConsole.getConsoleEditor(), "Pause the process to use command-line.");
    }
  }

  private void setEnabled(boolean flag) {
    myEnabled = flag;
  }

  public boolean isEnabled() {
    return myEnabled;
  }

  @Override
  public void sessionPaused() {
    setEnabled(true);
  }

  @Override
  public void sessionResumed() {
    setEnabled(false);
  }

  @Override
  public void sessionStopped() {
    setEnabled(false);
  }

  @Override
  public void stackFrameChanged() {
  }

  @Override
  public void beforeSessionResume() {
  }
}
