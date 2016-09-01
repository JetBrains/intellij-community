package org.jetbrains.debugger.memory.tracking;

import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.ui.breakpoints.JavaLineBreakpointType;
import com.intellij.debugger.ui.breakpoints.LineBreakpoint;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import com.intellij.xdebugger.impl.breakpoints.LineBreakpointState;
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointImpl;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.event.LocatableEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.EventRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.debugger.memory.component.CreationPositionTracker;
import org.jetbrains.java.debugger.breakpoints.properties.JavaLineBreakpointProperties;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class ConstructorInstancesTracker implements TrackerForNewInstances {
  private final HashSet<ObjectReference> myNewObjects = new HashSet<>();
  private final ReferenceType myReference;
  private final XDebugSession myDebugSession;
  private final CreationPositionTracker myPositionTracker;

  public ConstructorInstancesTracker(@NotNull ReferenceType ref,
                                     @NotNull XDebugSession debugSession) {
    myReference = ref;
    myDebugSession = debugSession;
    myPositionTracker = CreationPositionTracker.getInstance(debugSession.getProject());
    Project project = debugSession.getProject();
    JavaLineBreakpointType breakPointType = new JavaLineBreakpointType();

    XBreakpoint bpn = new XLineBreakpointImpl<>(breakPointType,
        ((XDebuggerManagerImpl) XDebuggerManagerImpl.getInstance(project)).getBreakpointManager(),
        new JavaLineBreakpointProperties(),
        new LineBreakpointState<>());
    MyConstructorBreakpoints myLineBreakpoint = new MyConstructorBreakpoints(project, bpn);
    DebugProcessImpl debugProcess = (DebugProcessImpl) DebuggerManager.getInstance(project)
        .getDebugProcess(debugSession.getDebugProcess().getProcessHandler());
    myLineBreakpoint.createRequestForPreparedClass(debugProcess, ref);
  }

  public void obsolete() {
    myNewObjects.clear();
    myPositionTracker.releaseBySession(myDebugSession);
  }

  @Override
  public List<ObjectReference> getNewInstances() {
    return new ArrayList<>(myNewObjects);
  }

  private final class MyConstructorBreakpoints extends LineBreakpoint<JavaLineBreakpointProperties> {
    private final List<BreakpointRequest> myRequests = new ArrayList<>();

    MyConstructorBreakpoints(Project project, XBreakpoint xBreakpoint) {
      super(project, xBreakpoint);
    }

    @Override
    protected void createRequestForPreparedClass(DebugProcessImpl debugProcess, ReferenceType classType) {
      for (Method cons : classType.methodsByName("<init>")) {
        Location loc = cons.location();
        BreakpointRequest breakpointRequest = debugProcess.getRequestsManager().createBreakpointRequest(this, loc);
        breakpointRequest.enable();
        myRequests.add(breakpointRequest);
      }
    }

    void disable(@NotNull DebugProcessImpl debugProcess) {
      myRequests.forEach(EventRequest::disable);
      myRequests.clear();
      debugProcess.getRequestsManager().deleteRequest(this);
    }

    @Override
    public void reload() {
    }

    @Override
    public boolean processLocatableEvent(SuspendContextCommandImpl action, LocatableEvent event)
        throws EventProcessingException {
      try {
        SuspendContextImpl suspendContext = action.getSuspendContext();
        if (suspendContext != null) {
          ObjectReference thisRef = getThisObject(suspendContext, event);
          if (myReference.equals(thisRef.referenceType())) {
            myNewObjects.add(thisRef);
            ThreadReferenceProxyImpl threadReferenceProxy = suspendContext.getThread();
            List<StackFrameProxyImpl> stack = threadReferenceProxy == null ? null : threadReferenceProxy.frames();
            if(stack != null) {
              myPositionTracker.addStack(myDebugSession, thisRef, stack);
            }
          }
        }
      } catch (EvaluateException e) {
        e.printStackTrace();
      }
      return false;
    }
  }
}
