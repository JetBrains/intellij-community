package com.intellij.xdebugger.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.breakpoints.XBreakpointListener;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author nik
 */
public class XDebugSessionImpl implements XDebugSession {
  private static final Logger LOG = Logger.getInstance("#com.intellij.xdebugger.impl.XDebugSessionImpl");
  private XDebugProcess myDebugProcess;
  private Set<XBreakpoint<?>> myRegisteredBreakpoints = new HashSet<XBreakpoint<?>>();
  private boolean myBreakpointsMuted;
  private boolean myBreakpointsDisabled;
  private final XDebuggerManagerImpl myDebuggerManager;
  private MyBreakpointListener myBreakpointListener;
  private boolean myPaused;

  public XDebugSessionImpl(XDebuggerManagerImpl debuggerManager) {
    myDebuggerManager = debuggerManager;
  }

  @NotNull
  public Project getProject() {
    return myDebuggerManager.getProject();
  }

  @NotNull
  public XDebugProcess getDebugProcess() {
    return myDebugProcess;
  }

  public boolean isPaused() {
    return myPaused;
  }

  public void init(final XDebugProcess process) {
    LOG.assertTrue(myDebugProcess == null);
    myDebugProcess = process;
    processAllBreakpoints(true);
    myBreakpointListener = new MyBreakpointListener();
    myDebuggerManager.getBreakpointManager().addBreakpointListener(myBreakpointListener);
  }

  private <B extends XBreakpoint<?>> void processBreakpoints(final XBreakpointHandler<B> handler, boolean register) {
    XBreakpointType<B,?> type = XDebuggerUtil.getInstance().findBreakpointType(handler.getBreakpointTypeClass());
    Collection<? extends B> breakpoints = myDebuggerManager.getBreakpointManager().getBreakpoints(type);
    for (B b : breakpoints) {
      handleBreakpoint(handler, b, register);
    }
  }

  private <B extends XBreakpoint<?>> void handleBreakpoint(final XBreakpointHandler<B> handler, final B b, final boolean register) {
    if (register && isBreakpointActive(b)) {
      myRegisteredBreakpoints.add(b);
      handler.registerBreakpoint(b);
    }
    if (!register && myRegisteredBreakpoints.contains(b)) {
      myRegisteredBreakpoints.remove(b);
      handler.unregisterBreakpoint(b);
    }
  }

  private void processAllHandlers(final XBreakpoint<?> breakpoint, final boolean register) {
    for (XBreakpointHandler<?> handler : myDebugProcess.getBreakpointHandlers()) {
      processBreakpoint(breakpoint, handler, register);
    }
  }

  private <B extends XBreakpoint<?>> void processBreakpoint(final XBreakpoint<?> breakpoint, final XBreakpointHandler<B> handler, boolean register) {
    XBreakpointType<?, ?> type = breakpoint.getType();
    if (handler.getBreakpointTypeClass().equals(type.getClass())) {
      B b = (B)breakpoint;
      handleBreakpoint(handler, b, register);
    }
  }

  private boolean isBreakpointActive(final XBreakpoint<?> b) {
    return !myBreakpointsMuted && b.isEnabled();
  }

  public boolean areBreakpointsMuted() {
    return myBreakpointsMuted;
  }

  public void setBreakpointMuted(boolean muted) {
    if (myBreakpointsMuted == muted) return;
    myBreakpointsMuted = muted;
    processAllBreakpoints(!muted);
  }

  public void stepOver() {
    doResume();
    myDebugProcess.startStepOver();
  }

  public void stepInto() {
    doResume();
    myDebugProcess.startStepInto();
  }

  public void stepOut() {
    doResume();
    myDebugProcess.startStepOut();
  }

  public void forceStepInto() {
    stepInto();
  }

  public void forceStepOver() {
    disableBreakpoints();
    stepOver();
  }

  private void processAllBreakpoints(final boolean register) {
    for (XBreakpointHandler<?> handler : myDebugProcess.getBreakpointHandlers()) {
      processBreakpoints(handler, register);
    }
  }

  private void disableBreakpoints() {
    myBreakpointsDisabled = true;
    processAllBreakpoints(false);
  }

  public void resume() {
    doResume();
    myDebugProcess.resume();
  }

  private void doResume() {
    myDebuggerManager.updateExecutionPosition(this, null);
    myPaused = false;
  }

  public void showExecutionPoint() {
    myDebuggerManager.showExecutionPosition();
  }

  public void breakpointReached(@NotNull final XBreakpoint<?> breakpoint) {
    myPaused = true;
    XSourcePosition position = breakpoint.getSourcePosition();
    if (position != null) {
      positionReached(position);
    }
  }

  public void positionReached(@NotNull final XSourcePosition position) {
    if (myBreakpointsDisabled) {
      myBreakpointsDisabled = false;
      new ReadAction() {
        protected void run(final Result result) {
          processAllBreakpoints(true);
        }
      }.execute();
    }
    myPaused = true;
    myDebuggerManager.updateExecutionPosition(this, position);
  }

  public void stop() {
    myDebugProcess.stop();
    myDebuggerManager.updateExecutionPosition(this, null);
    myDebuggerManager.getBreakpointManager().removeBreakpointListener(myBreakpointListener);
    myDebuggerManager.removeSession(this);
  }

  private class MyBreakpointListener implements XBreakpointListener<XBreakpoint<?>> {
    public void breakpointAdded(@NotNull final XBreakpoint<?> breakpoint) {
      processAllHandlers(breakpoint, true);
    }

    public void breakpointRemoved(@NotNull final XBreakpoint<?> breakpoint) {
      processAllHandlers(breakpoint, false);
    }

    public void breakpointChanged(@NotNull final XBreakpoint<?> breakpoint) {
      breakpointRemoved(breakpoint);
      breakpointAdded(breakpoint);
    }
  }

}
