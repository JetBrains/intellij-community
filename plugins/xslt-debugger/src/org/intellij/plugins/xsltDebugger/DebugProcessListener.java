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

import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.intellij.plugins.xsltDebugger.rt.engine.DebuggerStoppedException;
import org.jetbrains.annotations.NotNull;

/**
 * ProcessListener that manages the connection to the debugged XSLT-process
 */
class DebugProcessListener extends ProcessAdapter {
  private final Project myProject;
  private final int myPort;

  public DebugProcessListener(Project project, int port) {
    myProject = project;
    myPort = port;
  }

  @Override
  public void startNotified(@NotNull ProcessEvent event) {
    final DebuggerConnector connector = new DebuggerConnector(myProject, event.getProcessHandler(), myPort);
    ApplicationManager.getApplication().executeOnPooledThread(connector);
  }

  @Override
  public void processWillTerminate(@NotNull ProcessEvent event, boolean willBeDestroyed) {
    try {
      final XsltDebuggerSession session = XsltDebuggerSession.getInstance(event.getProcessHandler());
      if (session != null) {
        session.stop();
      }
    } catch (VMPausedException e) {
      // VM is paused, no way for a "clean" shutdown
    } catch (DebuggerStoppedException e) {
      // OK
    }

    super.processWillTerminate(event, willBeDestroyed);
  }

  @Override
  public void processTerminated(@NotNull ProcessEvent event) {
    super.processTerminated(event);

    final XsltDebuggerSession session = XsltDebuggerSession.getInstance(event.getProcessHandler());
    if (session != null) {
      session.close();
    }
  }
}
