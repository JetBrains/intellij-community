package com.jetbrains.python.console;

import com.intellij.openapi.project.Project;
import com.intellij.util.EventDispatcher;
import com.intellij.xdebugger.XStackFrameAwareSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.jetbrains.python.debugger.PyDebuggerEditorsProvider;
import com.jetbrains.python.debugger.PyStackFrame;
import com.jetbrains.python.debugger.PyStackFrameInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

/**
 * @author traff
 */
public class PyConsoleStackFrameConnector implements XStackFrameAwareSession {
  private Project myProject;
  private PydevConsoleCommunication myCommunication;

  private final EventDispatcher<XDebugSessionListener> myDispatcher = EventDispatcher.create(XDebugSessionListener.class);

  public PyConsoleStackFrameConnector(Project project, PydevConsoleCommunication communication) {
    myProject = project;
    myCommunication = communication;
  }

  @NotNull
  @Override
  public Project getProject() {
    return myProject;
  }

  @Nullable
  @Override
  public XStackFrame getCurrentStackFrame() {
    return new PyStackFrame(myProject, myCommunication, new PyStackFrameInfo("", "", "", null), null);
  }

  @Override
  public void setCurrentStackFrame(@NotNull XExecutionStack stack, @NotNull XStackFrame frame) {
    throw new IllegalStateException("Cant set stack frame to console.");
  }

  @Override
  public void addSessionListener(@NotNull final XDebugSessionListener listener) {
    myDispatcher.addListener(listener);
  }

  @Override
  public void removeSessionListener(@NotNull final XDebugSessionListener listener) {
    myDispatcher.removeListener(listener);
  }

  @NotNull
  @Override
  public XDebuggerEditorsProvider getEditorsProvider() {
    return new PyDebuggerEditorsProvider();
  }

  @Override
  public void reportError(@NotNull String message) {
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

  @Nullable
  @Override
  public XSuspendContext getSuspendContext() {
    return null;
  }

  public void resume() {
    myDispatcher.getMulticaster().sessionResumed();
  }
}
