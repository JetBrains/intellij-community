package com.jetbrains.python.console;

import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.util.EventDispatcher;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.impl.XSourcePositionImpl;
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler;
import com.intellij.xdebugger.stepping.XSmartStepIntoVariant;
import com.jetbrains.python.debugger.PyDebuggerEditorsProvider;
import com.jetbrains.python.debugger.PyStackFrame;
import com.jetbrains.python.debugger.PyStackFrameInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import javax.swing.*;
import javax.swing.event.HyperlinkListener;

/**
 * @author traff
 */
public class DebugSessionConsoleAdapter implements XDebugSession {
  private Project myProject;
  private PydevConsoleCommunication myCommunication;

  private final EventDispatcher<XDebugSessionListener> myDispatcher = EventDispatcher.create(XDebugSessionListener.class);

  public DebugSessionConsoleAdapter(Project project, PydevConsoleCommunication communication) {
    myProject = project;
    myCommunication = communication;
  }

  @NotNull
  @Override
  public Project getProject() {
    return myProject;
  }

  @NotNull
  @Override
  public XDebugProcess getDebugProcess() {
    return new XDebugProcess(this) {
      @NotNull
      @Override
      public XDebuggerEditorsProvider getEditorsProvider() {
        return new PyDebuggerEditorsProvider();
      }

      @Override
      public void startStepOver() {
        throw new NotImplementedException();
      }

      @Override
      public void startStepInto() {
        throw new NotImplementedException();
      }

      @Override
      public void startStepOut() {
        throw new NotImplementedException();
      }

      @Override
      public void stop() {
        throw new NotImplementedException();
      }

      @Override
      public void resume() {
        throw new NotImplementedException();
      }

      @Override
      public void runToPosition(@NotNull XSourcePosition position) {
        throw new NotImplementedException();
      }
    };
  }

  @Override
  public boolean isSuspended() {
    return !myCommunication.isExecuting();
  }

  @Nullable
  @Override
  public XStackFrame getCurrentStackFrame() {
    return new PyStackFrame(myProject, myCommunication, new PyStackFrameInfo("", "", "", null), null);
  }

  @Override
  public XSuspendContext getSuspendContext() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Nullable
  @Override
  public XSourcePosition getCurrentPosition() {
    return new XSourcePosition() {
      @Override
      public int getLine() {
        return -1;
      }

      @Override
      public int getOffset() {
        return -1;
      }

      @NotNull
      @Override
      public VirtualFile getFile() {
        return myCommunication.getConsoleFile();
      }

      @NotNull
      @Override
      public Navigatable createNavigatable(@NotNull Project project) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
      }
    };
  }

  @Override
  public void stepOver(boolean ignoreBreakpoints) {
    throw new NotImplementedException();
  }

  @Override
  public void stepInto() {
    throw new NotImplementedException();
  }

  @Override
  public void stepOut() {
    throw new NotImplementedException();
  }

  @Override
  public void forceStepInto() {
    throw new NotImplementedException();
  }

  @Override
  public void runToPosition(@NotNull XSourcePosition position, boolean ignoreBreakpoints) {
    throw new NotImplementedException();
  }

  @Override
  public void pause() {
    throw new NotImplementedException();
  }

  @Override
  public void resume() {
    myDispatcher.getMulticaster().sessionResumed();
  }

  @Override
  public void showExecutionPoint() {
    throw new NotImplementedException();
  }

  @Override
  public void setCurrentStackFrame(@NotNull XExecutionStack executionStack, @NotNull XStackFrame frame) {
    throw new NotImplementedException();
  }

  @Override
  public void setCurrentStackFrame(@NotNull XStackFrame frame) {
    throw new NotImplementedException();
  }

  @Override
  public void updateBreakpointPresentation(@NotNull XLineBreakpoint<?> breakpoint, @Nullable Icon icon, @Nullable String errorMessage) {
    throw new NotImplementedException();
  }

  @Override
  public boolean breakpointReached(@NotNull XBreakpoint<?> breakpoint,
                                   @Nullable String evaluatedLogExpression,
                                   @NotNull XSuspendContext suspendContext) {
    throw new NotImplementedException();
  }

  @Override
  public boolean breakpointReached(@NotNull XBreakpoint<?> breakpoint, @NotNull XSuspendContext suspendContext) {
    throw new NotImplementedException();
  }

  @Override
  public void positionReached(@NotNull XSuspendContext suspendContext) {
    throw new NotImplementedException();
  }

  @Override
  public void sessionResumed() {
    throw new NotImplementedException();
  }

  @Override
  public void stop() {
    throw new NotImplementedException();
  }

  @Override
  public void setBreakpointMuted(boolean muted) {
    throw new NotImplementedException();
  }

  @Override
  public boolean areBreakpointsMuted() {
    throw new NotImplementedException();
  }

  @Override
  public void addSessionListener(@NotNull final XDebugSessionListener listener) {
    myDispatcher.addListener(listener);
  }

  @Override
  public void removeSessionListener(@NotNull final XDebugSessionListener listener) {
    myDispatcher.removeListener(listener);
  }

  @Override
  public void reportError(@NotNull String message) {
    throw new NotImplementedException();
  }

  @Override
  public void reportMessage(@NotNull String message, @NotNull MessageType type) {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public void reportMessage(@NotNull String message, @NotNull MessageType type, @Nullable HyperlinkListener listener) {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  @NotNull
  @Override
  public String getSessionName() {
    return "Python Console"; //TODO
  }

  @NotNull
  @Override
  public RunContentDescriptor getRunContentDescriptor() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Nullable
  @Override
  public RunProfile getRunProfile() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public void setPauseActionSupported(boolean isSupported) {
    throw new NotImplementedException();
  }

  @Override
  public void setAutoInitBreakpoints(boolean value) {
    throw new NotImplementedException();
  }

  @Override
  public void rebuildViews() {
    throw new NotImplementedException();
  }

  @Override
  public <V extends XSmartStepIntoVariant> void smartStepInto(XSmartStepIntoHandler<V> handler, V variant) {
    throw new NotImplementedException();
  }

  @Override
  public void updateExecutionPosition() {
    throw new NotImplementedException();
  }

  @Override
  public void initBreakpoints() {
    throw new NotImplementedException();
  }

  @Override
  public ConsoleView getConsoleView() {
    throw new NotImplementedException();
  }

  @Override
  public RunnerLayoutUi getUI() {
    throw new NotImplementedException();
  }

  @Override
  public boolean isStopped() {
    return false; //TODO
  }

  @Override
  public boolean isPaused() {
    return false;
  }
}
