/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.console;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.remote.RemoteProcessControl;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebugSession;
import com.jetbrains.python.debugger.PyDebugProcess;
import org.jetbrains.annotations.NotNull;

import java.net.ServerSocket;

/**
 * @author traff
 */
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
    return "Connecting to console...";
  }

  @Override
  protected String getConnectionTitle() {
    return "Debugger connection";
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
  protected void afterConnect() {
  }


  @Override
  public int getConnectTimeout() {
    return 0; //server should not stop
  }

  public void connect(PydevConsoleCommunication consoleCommunication) throws Exception {
    int portToConnect;
    if (myConsoleDebugProcessHandler.getConsoleProcessHandler() instanceof RemoteProcessControl) {
      portToConnect = getRemoteTunneledPort(myLocalPort,
                                            ((RemoteProcessControl)myConsoleDebugProcessHandler.getConsoleProcessHandler()));
    } else {
      portToConnect = myLocalPort;
    }
    consoleCommunication.connectToDebugger(portToConnect);
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

      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          waitForConnection(getCurrentStateMessage(), getConnectionTitle());
        }
      });
    }
  }
}
