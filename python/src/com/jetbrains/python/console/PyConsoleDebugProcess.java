// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console;

import com.google.common.collect.Maps;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.remote.RemoteProcessControl;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebugSession;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.debugger.PyDebugProcess;
import com.jetbrains.python.debugger.PyDebugRunner;
import com.jetbrains.python.debugger.PyDebuggerOptionsProvider;
import org.jetbrains.annotations.NotNull;

import java.net.ServerSocket;
import java.util.Map;

public class PyConsoleDebugProcess extends PyDebugProcess {
  private final int myLocalPort;
  private final PyConsoleDebugProcessHandler myConsoleDebugProcessHandler;

  public PyConsoleDebugProcess(@NotNull XDebugSession session,
                               @NotNull final ServerSocket serverSocket,
                               @NotNull final ExecutionConsole executionConsole,
                               @NotNull final PyConsoleDebugProcessHandler consoleDebugProcessHandler) {
    super(session, serverSocket, executionConsole, consoleDebugProcessHandler, false);
    myLocalPort = serverSocket.getLocalPort();
    myConsoleDebugProcessHandler = consoleDebugProcessHandler;
  }

  @Override
  public void sessionInitialized() {
    //nop
  }

  @Override
  protected String getConnectionMessage() {
    return PyBundle.message("progress.text.connecting.to.console");
  }

  @Override
  protected String getConnectionTitle() {
    return PyBundle.message("progress.title.debugger.connection");
  }

  @Override
  protected void detachDebuggedProcess() {
    //TODO: implement disconnect
  }

  @Override
  protected void beforeConnect() {
    printToConsole(getCurrentStateMessage() + "\n", ConsoleViewContentType.SYSTEM_OUTPUT);
  }


  @Override
  public int getConnectTimeout() {
    return 0; //server should not stop
  }

  public void connect(PydevConsoleCommunication consoleCommunication) throws Exception {
    Pair<String, Integer> portToConnect;
    if (myConsoleDebugProcessHandler.getConsoleProcessHandler() instanceof RemoteProcessControl) {
      portToConnect = getRemoteHostPortForDebuggerConnection(myLocalPort,
                                                             ((RemoteProcessControl)myConsoleDebugProcessHandler.getConsoleProcessHandler()));
    }
    else {
      portToConnect = Pair.create("localhost", myLocalPort);
    }
    Map<String, Boolean> optionsMap = makeDebugOptionsMap(getSession());
    Map<String, String> envs = getDebuggerEnvs(getSession());
    consoleCommunication.connectToDebugger(portToConnect.getSecond(), portToConnect.getFirst(), optionsMap, envs);
  }

  public static Map<String, String> getDebuggerEnvs(XDebugSession session) {
    Map<String, String> env = Maps.newHashMap();
    PyDebugRunner.configureDebugEnvironment(session.getProject(), env, session.getRunProfile());
    return env;
  }

  public static Map<String, Boolean> makeDebugOptionsMap(XDebugSession session) {
    Project project = session.getProject();
    PyDebuggerOptionsProvider userOpts = PyDebuggerOptionsProvider.getInstance(project);
    Map<String, Boolean> dbgOpts = Maps.newHashMap();
    dbgOpts.put("save-signatures", userOpts.isSaveCallSignatures());
    dbgOpts.put("qt-support", userOpts.isSupportQtDebugging());
    return dbgOpts;
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

      UIUtil.invokeLaterIfNeeded(() -> waitForConnection(getCurrentStateMessage(), getConnectionTitle()));
    }
  }
}
