package com.jetbrains.python.debugger;

import com.intellij.execution.console.LanguageConsoleViewImpl;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.xdebugger.XDebugSessionListener;
import com.jetbrains.python.console.PydevConsoleExecuteActionHandler;
import com.jetbrains.python.console.pydev.ConsoleCommunication;

/**
 * @author traff
 */
public class PydevDebugConsoleExecuteActionHandler extends PydevConsoleExecuteActionHandler implements XDebugSessionListener {

  public PydevDebugConsoleExecuteActionHandler(LanguageConsoleViewImpl consoleView,
                                               ProcessHandler myProcessHandler,
                                               ConsoleCommunication consoleCommunication) {
    super(consoleView, myProcessHandler, consoleCommunication);
  }

  @Override
  protected String getConsoleIsNotEnabledMessage() {
    return "Pause the process to use command-line.";
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
