package com.intellij.xdebugger.impl;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.*;
import com.intellij.xdebugger.impl.breakpoints.CustomizedBreakpointPresentation;
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointImpl;
import com.intellij.xdebugger.breakpoints.*;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XSuspendContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
public class XDebugSessionImpl implements XDebugSession {
  private static final Logger LOG = Logger.getInstance("#com.intellij.xdebugger.impl.XDebugSessionImpl");
  private XDebugProcess myDebugProcess;
  private final Map<XBreakpoint<?>, CustomizedBreakpointPresentation> myRegisteredBreakpoints = new HashMap<XBreakpoint<?>, CustomizedBreakpointPresentation>();
  private boolean myBreakpointsMuted;
  private boolean myBreakpointsDisabled;
  private final XDebuggerManagerImpl myDebuggerManager;
  private MyBreakpointListener myBreakpointListener;
  private XSuspendContext mySuspendContext;
  private XSourcePosition myCurrentPosition;
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

  public boolean isSuspended() {
    return myPaused && mySuspendContext != null;
  }

  public boolean isPaused() {
    return myPaused;
  }

  public XSuspendContext getSuspendContext() {
    return mySuspendContext;
  }

  @Nullable
  public XSourcePosition getCurrentPosition() {
    return myCurrentPosition;
  }

  public void init(final XDebugProcess process) {
    LOG.assertTrue(myDebugProcess == null);
    myDebugProcess = process;
    processAllBreakpoints(true, false);
    myBreakpointListener = new MyBreakpointListener();
    myDebuggerManager.getBreakpointManager().addBreakpointListener(myBreakpointListener);
    process.sessionInitialized();
  }

  private <B extends XBreakpoint<?>> void processBreakpoints(final XBreakpointHandler<B> handler, boolean register, final boolean temporary) {
    XBreakpointType<B,?> type = XDebuggerUtil.getInstance().findBreakpointType(handler.getBreakpointTypeClass());
    Collection<? extends B> breakpoints = myDebuggerManager.getBreakpointManager().getBreakpoints(type);
    for (B b : breakpoints) {
      handleBreakpoint(handler, b, register, temporary);
    }
  }

  private <B extends XBreakpoint<?>> void handleBreakpoint(final XBreakpointHandler<B> handler, final B b, final boolean register,
                                                           final boolean temporary) {
    if (register && isBreakpointActive(b)) {
      synchronized (myRegisteredBreakpoints) {
        myRegisteredBreakpoints.put(b, new CustomizedBreakpointPresentation());
      }
      handler.registerBreakpoint(b);
    }
    if (!register) {
      boolean removed = false;
      synchronized (myRegisteredBreakpoints) {
        if (myRegisteredBreakpoints.containsKey(b)) {
          myRegisteredBreakpoints.remove(b);
          removed = true;
        }
      }
      if (removed) {
        handler.unregisterBreakpoint(b, temporary);
      }
    }
  }

  @Nullable
  public CustomizedBreakpointPresentation getBreakpointPresentation(@NotNull XLineBreakpoint<?> breakpoint) {
    synchronized (myRegisteredBreakpoints) {
      return myRegisteredBreakpoints.get(breakpoint);
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
      handleBreakpoint(handler, b, register, false);
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
    processAllBreakpoints(!muted, muted);
  }

  public void stepOver(final boolean ignoreBreakpoints) {
    if (ignoreBreakpoints) {
      disableBreakpoints();
    }
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

  public void runToPosition(@NotNull final XSourcePosition position, final boolean ignoreBreakpoints) {
    if (ignoreBreakpoints) {
      disableBreakpoints();
    }
    doResume();
    myDebugProcess.runToPosition(position);
  }

  private void processAllBreakpoints(final boolean register, final boolean temporary) {
    for (XBreakpointHandler<?> handler : myDebugProcess.getBreakpointHandlers()) {
      processBreakpoints(handler, register, temporary);
    }
  }

  private void disableBreakpoints() {
    myBreakpointsDisabled = true;
    processAllBreakpoints(false, true);
  }

  public void resume() {
    doResume();
    myDebugProcess.resume();
  }

  private void doResume() {
    myDebuggerManager.updateExecutionPosition(this, null);
    mySuspendContext = null;
    myCurrentPosition = null;
    myPaused = false;
  }

  public void showExecutionPoint() {
    myDebuggerManager.showExecutionPosition();
  }

  public void updateBreakpointPresentation(@NotNull final XLineBreakpoint<?> breakpoint, @Nullable final Icon icon, @Nullable final String errorMessage) {
    CustomizedBreakpointPresentation presentation;
    synchronized (myRegisteredBreakpoints) {
      presentation = myRegisteredBreakpoints.get(breakpoint);
      if (presentation != null) {
        presentation.setErrorMessage(errorMessage);
        presentation.setIcon(icon);
      }
    }
    if (presentation != null) {
      myDebuggerManager.getBreakpointManager().getLineBreakpointManager().queueBreakpointUpdate((XLineBreakpointImpl<?>)breakpoint);
    }
  }

  public boolean breakpointReached(@NotNull final XBreakpoint<?> breakpoint, @NotNull final XSuspendContext suspendContext) {
    XDebuggerEvaluator evaluator = suspendContext.getEvaluator();
    String condition = breakpoint.getCondition();
    if (condition != null && evaluator != null) {
      LOG.debug("evaluating condition: " + condition);
      boolean result = evaluator.evaluateCondition(condition);
      LOG.debug("condition evaluates to " + result);
      if (!result) {
        return false;
      }
    }

    if (breakpoint.isLogMessage()) {
      String message = getBreakpointLogMessage(breakpoint);
      printMessage(message);
    }
    String expression = breakpoint.getLogExpression();
    if (expression != null && evaluator != null) {
      LOG.debug("evaluating log expression: " + expression);
      printMessage(evaluator.evaluateMessage(expression));
    }

    if (breakpoint.getSuspendPolicy() == SuspendPolicy.NONE) {
      return false;
    }

    myPaused = true;
    XSourcePosition position = breakpoint.getSourcePosition();
    if (position != null) {
      positionReached(position, suspendContext);
    }
    return true;
  }

  private static void printMessage(final String message) {
    //todo[nik]
    LOG.info(message);
  }

  private static <B extends XBreakpoint<?>> String getBreakpointLogMessage(final B breakpoint) {
    return XDebuggerBundle.message("xbreakpoint.reached.at.0", XDebuggerUtilImpl.getType(breakpoint).getDisplayText(breakpoint));
  }

  public void positionReached(@NotNull final XSourcePosition position, @NotNull final XSuspendContext suspendContext) {
    enableBreakpoints();
    mySuspendContext = suspendContext;
    myCurrentPosition = position;
    myPaused = true;
    myDebuggerManager.updateExecutionPosition(this, position);
  }

  private void enableBreakpoints() {
    if (myBreakpointsDisabled) {
      myBreakpointsDisabled = false;
      new ReadAction() {
        protected void run(final Result result) {
          processAllBreakpoints(true, false);
        }
      }.execute();
    }
  }

  public void stop() {
    myDebugProcess.stop();
    myDebuggerManager.updateExecutionPosition(this, null);
    myDebuggerManager.getBreakpointManager().removeBreakpointListener(myBreakpointListener);
    myDebuggerManager.removeSession(this);
  }

  private class MyBreakpointListener implements XBreakpointListener<XBreakpoint<?>> {
    public void breakpointAdded(@NotNull final XBreakpoint<?> breakpoint) {
      if (!myBreakpointsDisabled) {
        processAllHandlers(breakpoint, true);
      }
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
