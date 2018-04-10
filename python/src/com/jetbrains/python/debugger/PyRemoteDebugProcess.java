package com.jetbrains.python.debugger;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xdebugger.XDebugSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.ServerSocket;

/**
 * @author traff
 */
public class PyRemoteDebugProcess extends PyDebugProcess {
  private final int myLocalPort;
  private final String mySettraceCall;
  private boolean isStopCalled = false;

  public PyRemoteDebugProcess(@NotNull XDebugSession session,
                              @NotNull final ServerSocket serverSocket,
                              @NotNull final ExecutionConsole executionConsole,
                              @Nullable final ProcessHandler processHandler, @Nullable final String settraceCall) {
    super(session, serverSocket, executionConsole, processHandler, false);
    if (processHandler instanceof PyRemoteDebugProcessAware) {
      ((PyRemoteDebugProcessAware)processHandler).setRemoteDebugProcess(this);
    }
    myLocalPort = serverSocket.getLocalPort();
    mySettraceCall = settraceCall;
  }

  @Override
  public void sessionInitialized() {
    super.sessionInitialized();
    printConsoleInfo();
  }

  protected void printConsoleInfo() {
    printToConsole("Starting debug server at port " + myLocalPort + "\n", ConsoleViewContentType.SYSTEM_OUTPUT);
    printToConsole("Use the following code to connect to the debugger:\n", ConsoleViewContentType.SYSTEM_OUTPUT);
    if (!StringUtil.isEmpty(mySettraceCall)) {
      printToConsole(mySettraceCall + "\n", ConsoleViewContentType.SYSTEM_OUTPUT);
    }
  }

  @Override
  protected String getConnectionMessage() {
    return "Waiting for process connection...";
  }

  @Override
  protected String getConnectionTitle() {
    return "Waiting for connection";
  }

  @Override
  protected boolean shouldLogConnectionException(Exception e) {
    return !(isStopCalled && e.getMessage().contains("closed"));
  }

  @Override
  protected void detachDebuggedProcess() {
    waitForNextConnection(); // in case of remote debug we should wait for the next connection
  }

  @Override
  public void stop() {
    super.stop();
    isStopCalled = true;
  }

  @Override
  protected void beforeConnect() {
    printToConsole(getCurrentStateMessage() + "\n", ConsoleViewContentType.SYSTEM_OUTPUT);
  }

  @Override
  protected void afterConnect() {
  }

  public void waitForNextConnection() {
    if (isConnected()) {
      disconnect();
    }
    if (getSession().isSuspended()) {
      getSession().resume();
    }
    if (!isWaitingForConnection()) {
      setWaitingForConnection(true);
      ApplicationManager.getApplication().invokeLater(() -> waitForConnection(getCurrentStateMessage(), getConnectionTitle()),
                                                      ModalityState.defaultModalityState());
    }
  }

  @Override
  public int getConnectTimeout() {
    return 0; //server should not stop
  }
}
