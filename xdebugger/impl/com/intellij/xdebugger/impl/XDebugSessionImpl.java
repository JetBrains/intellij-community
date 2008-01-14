package com.intellij.xdebugger.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.breakpoints.*;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author nik
 */
public class XDebugSessionImpl implements XDebugSession {
  private static final Logger LOG = Logger.getInstance("#com.intellij.xdebugger.impl.XDebugSessionImpl");
  private XDebugProcess myDebugProcess;
  private final XDebuggerManagerImpl myDebuggerManager;
  private MyBreakpointListener myBreakpointListener;
  private boolean myPaused;

  public XDebugSessionImpl(XDebuggerManagerImpl debuggerManager) {
    myDebuggerManager = debuggerManager;
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
    for (XBreakpointHandler<?> handler : myDebugProcess.getBreakpointHandlers()) {
      registerBreakpoints(handler);
    }
    myBreakpointListener = new MyBreakpointListener();
    myDebuggerManager.getBreakpointManager().addBreakpointListener(myBreakpointListener);
  }

  private <B extends XBreakpoint<?>> void registerBreakpoints(final XBreakpointHandler<B> handler) {
    XBreakpointType<B,?> type = XDebuggerUtil.getInstance().findBreakpointType(handler.getBreakpointTypeClass());
    Collection<? extends B> breakpoints = myDebuggerManager.getBreakpointManager().getBreakpoints(type);
    for (B b : breakpoints) {
      handler.registerBreakpoint(b);
    }
  }

  public void dispose() {
    myDebuggerManager.getBreakpointManager().removeBreakpointListener(myBreakpointListener);
  }

  public void stepOver() {
  }

  public void stepInto() {
  }

  public void stepOut() {
  }

  public void forceStepInto() {
  }

  public void forceStepOver() {
  }

  public void resume() {
  }

  public void showExecutionPoint() {
  }

  private class MyBreakpointListener implements XBreakpointListener<XBreakpoint<?>> {
    public void breakpointAdded(@NotNull final XBreakpoint<?> breakpoint) {
      for (XBreakpointHandler<?> handler : myDebugProcess.getBreakpointHandlers()) {
        processBreakpoint(breakpoint, handler, true);
      }
    }

    private <B extends XBreakpoint<?>> void processBreakpoint(final XBreakpoint<?> breakpoint, final XBreakpointHandler<B> handler, boolean register) {
      XBreakpointType<?, ?> type = breakpoint.getType();
      if (handler.getBreakpointTypeClass().equals(type.getClass())) {
        B b = (B)breakpoint;
        if (register) {
          handler.registerBreakpoint(b);
        }
        else {
          handler.unregisterBreakpoint(b);
        }
      }
    }

    public void breakpointRemoved(@NotNull final XBreakpoint<?> breakpoint) {
      for (XBreakpointHandler<?> handler : myDebugProcess.getBreakpointHandlers()) {
        processBreakpoint(breakpoint, handler, false);
      }
    }

    public void breakpointChanged(@NotNull final XBreakpoint<?> breakpoint) {
      breakpointRemoved(breakpoint);
      breakpointAdded(breakpoint);
    }
  }

}
