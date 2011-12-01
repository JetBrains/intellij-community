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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.EventDispatcher;
import com.intellij.xdebugger.XSourcePosition;
import org.intellij.plugins.xsltDebugger.impl.XsltBreakpointHandler;
import org.intellij.plugins.xsltDebugger.impl.XsltDebugProcess;
import org.intellij.plugins.xsltDebugger.rt.engine.Breakpoint;
import org.intellij.plugins.xsltDebugger.rt.engine.BreakpointManager;
import org.intellij.plugins.xsltDebugger.rt.engine.Debugger;
import org.intellij.plugins.xsltDebugger.rt.engine.DebuggerStoppedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.rmi.RemoteException;
import java.util.EventListener;

/**
 * This is the main place that interacts with the debugged XSLT processor. Waits until the processor
 * hits a breakpoint, resumes execution, etc.
 */
public class XsltDebuggerSession implements Disposable {
  private static final Key<XsltDebuggerSession> DEBUGGER_SESSION = Key.create("DEBUGGER_SESSION");

  private final Project myProject;
  private final ProcessHandler myProcess;
  private final Debugger myClient;
  private final EventDispatcher<Listener> myEventDispatcher = EventDispatcher.create(Listener.class);

  private Breakpoint myTempBreakpoint;

  private volatile Debugger.State myState;
  private boolean myClosed;

  private XsltDebuggerSession(Project project, ProcessHandler process, Debugger client) {
    myProject = project;
    myProcess = process;
    myClient = client;
    Disposer.register(XsltDebugProcess.getInstance(process), this);
  }

  public void start() {
    myClient.start();
    myState = Debugger.State.RUNNING;

    final BreakpointManager breakpointManager = myClient.getBreakpointManager();
    final Listener multicaster = myEventDispatcher.getMulticaster();
    try {
      if (!myClient.waitForDebuggee()) {
        multicaster.debuggerStopped();
        return;
      }
      myState = Debugger.State.SUSPENDED;
      do {
        if (myState == Debugger.State.SUSPENDED) {
          if (myTempBreakpoint != null) {
            breakpointManager.removeBreakpoint(myTempBreakpoint);
            myTempBreakpoint = null;
          }
          multicaster.debuggerSuspended();
        } else if (myState == Debugger.State.RUNNING) {
          multicaster.debuggerResumed();
        } else if (myState == Debugger.State.STOPPED) {
          break;
        }
      }
      while ((myState = myClient.waitForStateChange(myState)) != null);

      multicaster.debuggerStopped();
    } catch (DebuggerStoppedException e) {
      multicaster.debuggerStopped();
    } catch (RuntimeException e) {
      if (e.getCause() instanceof RemoteException) {
        if (e.getCause().getCause() instanceof SocketException) {
          multicaster.debuggerStopped();
          return;
        }
      }
      throw e;
    } finally {
      myState = Debugger.State.STOPPED;
      close();
    }
  }

  public void addListener(Listener listener) {
    myEventDispatcher.addListener(listener);
  }

  public void removeListener(Listener listener) {
    myEventDispatcher.removeListener(listener);
  }

  public Debugger getClient() {
    return myClient;
  }

  public Debugger.State getCurrentState() {
    return myState;
  }

  public void pause() {
    myClient.pause();
  }

  public void resume() {
    myClient.resume();
  }

  public void stop() {
    try {
      myClient.stop(false);
    } catch (DebuggerStoppedException ignore) {
    }
  }

  public void stepOver() {
    myClient.step();
  }

  public void stepInto() {
    myClient.stepInto();
  }

  public boolean canRunTo(final XSourcePosition position) {
    return XsltBreakpointHandler.getActualLineNumber(myProject, position) != -1;
  }

  public void runTo(final PsiFile file, final XSourcePosition position) {
    assert myTempBreakpoint == null;

    final int lineNumber = XsltBreakpointHandler.getActualLineNumber(myProject, position);
    final String uri = XsltBreakpointHandler.getFileURL(file.getVirtualFile());
    myTempBreakpoint = myClient.getBreakpointManager().setBreakpoint(uri, lineNumber);

    resume();
  }

  @Nullable
  public static Editor openLocation(Project project, @NotNull String uri, int lineNumber) {
    try {
      final VirtualFile file = VfsUtil.findFileByURL(new URI(uri).toURL());
      final OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file, lineNumber, 0);
      descriptor.navigate(true);

      return FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
    } catch (MalformedURLException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
      return null;
    } catch (URISyntaxException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
      return null;
    }
  }

  public synchronized void close() {
    if (myClosed) return;
    myClosed = true;

    try {
      myClient.stop(true);
    } catch (DebuggerStoppedException e) {
      // OK
    } finally {
      myProcess.destroyProcess();
    }
  }

  @Override
  public void dispose() {
    detach(myProcess);
  }

  @NotNull
  public static XsltDebuggerSession create(Project project, @NotNull ProcessHandler process, Debugger client) {
    final XsltDebuggerSession session = new XsltDebuggerSession(project, process, client);
    process.putUserData(DEBUGGER_SESSION, session);
    return session;
  }

  public static XsltDebuggerSession getInstance(@NotNull ProcessHandler process) {
    return process.getUserData(DEBUGGER_SESSION);
  }

  public static void detach(ProcessHandler processHandler) {
    processHandler.putUserData(DEBUGGER_SESSION, null);
  }

  public interface Listener extends EventListener {
    void debuggerSuspended();

    void debuggerResumed();

    void debuggerStopped();
  }
}
