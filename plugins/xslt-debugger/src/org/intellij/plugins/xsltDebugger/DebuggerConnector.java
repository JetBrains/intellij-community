/*
 * Copyright 2007 Sascha Weinreuter
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

package org.intellij.plugins.xsltDebugger;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.intellij.plugins.xsltDebugger.impl.XsltDebugProcess;
import org.intellij.plugins.xsltDebugger.rt.engine.Debugger;
import org.intellij.plugins.xsltDebugger.rt.engine.OutputEventQueue;
import org.intellij.plugins.xsltDebugger.rt.engine.remote.RemoteDebuggerClient;
import org.intellij.plugins.xsltDebugger.ui.StructureTabComponent;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.rmi.ConnectException;
import java.rmi.NotBoundException;

/**
 * Establishes the debugger-connection to the started XSLT-process, starts the debugger-session
 * and attaches the debugger-UI.
 */
class DebuggerConnector implements Runnable {

  private final Project myProject;
  private final ProcessHandler myProcess;
  private final int myPort;

  public DebuggerConnector(Project project, ProcessHandler process, int port) {
    myProject = project;
    myProcess = process;
    myPort = port;
  }

  public void run() {
    final Debugger client = connect();
    if (client == null) {
      // client will be null if the process terminated prematurely for some reason. no need for an error message
      if (!myProcess.isProcessTerminated()) {
        myProcess.notifyTextAvailable("Failed to connect to debugged process. Terminating.\n", ProcessOutputTypes.SYSTEM);
        myProcess.destroyProcess();
      }
      return;
    }

    final XsltDebuggerSession session = XsltDebuggerSession.create(myProject, myProcess, client);

    final XsltDebugProcess dbgp = XsltDebugProcess.getInstance(myProcess);
    assert dbgp != null;
    dbgp.init(client);

    session.addListener(new XsltDebuggerSession.Listener() {
      @Override
      public void debuggerSuspended() {
        final OutputEventQueue queue = client.getEventQueue();
        StructureTabComponent.getInstance(myProcess).getEventModel().update(queue.getEvents());
      }

      @Override
      public void debuggerResumed() {
      }

      @Override
      public void debuggerStopped() {
        try {
          final OutputEventQueue queue = client.getEventQueue();
          StructureTabComponent.getInstance(myProcess).getEventModel().finalUpdate(queue.getEvents());
        } catch (Exception e) {
          // can fail when debugger is manually terminated
        }
      }
    });

    session.start();
  }

  @Nullable
  private Debugger connect() {
    Throwable lastException = null;
    for (int i = 0; i < 10; i++) {
      if (myProcess.isProcessTerminated()) return null;

      try {
        final Debugger realClient = EDTGuard.create(new RemoteDebuggerClient(myPort), myProcess);
        myProcess.notifyTextAvailable("Connected to XSLT debugger on port " + myPort + "\n", ProcessOutputTypes.SYSTEM);
        return realClient;
      } catch (ConnectException e) {
        lastException = e;
        try {
          Thread.sleep(500);
        } catch (InterruptedException e1) {
          break;
        }
      } catch (NotBoundException e) {
        lastException = e;
        try {
          Thread.sleep(200);
        } catch (InterruptedException e1) {
          break;
        }
      } catch (IOException e) {
        lastException = e;
        break;
      }
    }

    if (lastException != null) {
      Logger.getInstance(getClass().getName()).info("Could not connect to debugger", lastException);

      if (lastException.getMessage() != null) {
        myProcess.notifyTextAvailable("Connection error: " + lastException.getMessage() + "\n", ProcessOutputTypes.SYSTEM);
      }
    }

    return null;
  }
}
