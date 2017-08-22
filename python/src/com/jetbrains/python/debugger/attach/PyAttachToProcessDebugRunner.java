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
package com.jetbrains.python.debugger.attach;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.jetbrains.python.debugger.PyDebugRunner;
import com.jetbrains.python.debugger.PyLocalPositionConverter;
import com.jetbrains.python.debugger.PyRemoteDebugProcess;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * @author traff
 */
public class PyAttachToProcessDebugRunner extends PyDebugRunner {
  private Project myProject;
  private final int myPid;
  private String mySdkPath;
  private static final int CONNECTION_TIMEOUT = 20000;


  public PyAttachToProcessDebugRunner(@NotNull Project project, int pid, String sdkPath) {
    myProject = project;
    myPid = pid;
    mySdkPath = sdkPath;
  }

  public XDebugSession launch() throws ExecutionException {
    FileDocumentManager.getInstance().saveAllDocuments();

    return launchRemoteDebugServer();
  }

  private XDebugSession launchRemoteDebugServer() throws ExecutionException {
    final ServerSocket serverSocket;
    try {
      //noinspection SocketOpenedButNotSafelyClosed
      serverSocket = new ServerSocket(0);
    }
    catch (IOException e) {
      throw new ExecutionException("Failed to find free socket port", e);
    }


    PyAttachToProcessCommandLineState state = PyAttachToProcessCommandLineState.create(myProject, mySdkPath, serverSocket.getLocalPort(), myPid);

    final ExecutionResult result = state.execute(state.getEnvironment().getExecutor(), this);

    //start remote debug server
    return XDebuggerManager.getInstance(myProject).
      startSessionAndShowTab(String.valueOf(myPid), null, new XDebugProcessStarter() {
        @org.jetbrains.annotations.NotNull
        public XDebugProcess start(@NotNull final XDebugSession session) {
          PyRemoteDebugProcess pyDebugProcess =
            new PyRemoteDebugProcess(session, serverSocket, result.getExecutionConsole(),
                                     result.getProcessHandler(), "") {
              @Override
              protected void printConsoleInfo() {
              }

              @Override
              public int getConnectTimeout() {
                return CONNECTION_TIMEOUT;
              }

              @Override
              protected void detachDebuggedProcess() {
                handleStop();
              }

              @Override
              protected String getConnectionMessage() {
                return "Attaching to a process with PID=" + myPid;
              }

              @Override
              protected String getConnectionTitle() {
                return "Attaching Debugger";
              }
            };
          pyDebugProcess.setPositionConverter(new PyLocalPositionConverter());


          createConsoleCommunicationAndSetupActions(myProject, result, pyDebugProcess, session);

          return pyDebugProcess;
        }
      });
  }
}
