package com.intellij.debugger.engine;

import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.engine.requests.LocatableEventRequestor;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.requests.Requestor;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.debugger.ui.breakpoints.LineBreakpoint;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.util.concurrency.Semaphore;
import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.EventRequest;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Feb 25, 2004
 * Time: 4:39:03 PM
 * To change this template use File | Settings | File Templates.
 */
public class DebugProcessEvents extends DebugProcessImpl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.DebugProcessEvents");
  private DebuggerEventThread myEventThread;

  public DebugProcessEvents(Project project) {
    super(project);
  }

  protected void commitVM(final VirtualMachine vm) {
    super.commitVM(vm);
    if(vm != null) {
      vmAttached();

      myEventThread = new DebuggerEventThread();
      myEventThread.start();

    }
  }

  private static void showStatusText(DebugProcessImpl debugProcess,  Event event) {
    Requestor requestor = debugProcess.getRequestsManager().findRequestor(event.request());
    Breakpoint breakpoint = null;
    if(requestor instanceof Breakpoint) {
      breakpoint = (Breakpoint)requestor;
    }
    String text = getEventText(new Pair<Breakpoint, Event>(breakpoint, event));
    debugProcess.showStatusText(text);
  }

  public static String getEventText(Pair<Breakpoint, Event> descriptor) {
    String text = "";
    Event event = descriptor.getSecond();
    if (event instanceof VMStartEvent) {
      text = "Process started";
    }
    else if (event instanceof VMDeathEvent) {
      text = "Process terminated";
    }
    else if (event instanceof VMDisconnectEvent) {
      text = "Disconnected from the debugged process";
    }
    else if (event instanceof ExceptionEvent) {
      ExceptionEvent exceptionEvent = (ExceptionEvent)event;
      ObjectReference objectReference = exceptionEvent.exception();
      try {
        text = "Exception breakpoint reached. Exception  '" + objectReference.referenceType().name() + "' in thread '" + exceptionEvent.thread().name() + "'";
      }
      catch (Exception e) {
        text = "Exception breakpoint reached. ";
      }
    }
    else if (event instanceof AccessWatchpointEvent) {
      AccessWatchpointEvent accessEvent = (AccessWatchpointEvent)event;
      StringBuffer message = new StringBuffer(32);
      message.append("Field watchpoint reached. ");
      ObjectReference object = accessEvent.object();
      Field field = accessEvent.field();
      if (object != null) {
        message.append("{");
      }
      message.append(field.declaringType().name());
      if (object != null) {
        message.append('@');
        message.append(object.uniqueID());
        message.append("}");
      }
      message.append('.');
      message.append(field.name());
      message.append(" will be accessed.");
      text = message.toString();
    }
    else if (event instanceof ModificationWatchpointEvent) {
      ModificationWatchpointEvent modificationEvent = (ModificationWatchpointEvent)event;
      StringBuffer message = new StringBuffer(64);
      message.append("Field watchpoint reached. ");
      Field field = modificationEvent.field();
      ObjectReference object = modificationEvent.object();
      if (object != null) {
        message.append("{");
      }
      message.append(field.declaringType().name());
      if (object != null) {
        message.append('@');
        message.append(object.uniqueID());
        message.append("}");
      }
      message.append('.');
      message.append(field.name());
      message.append(" will be modified. Current value = '");
      message.append(modificationEvent.valueCurrent());
      message.append("'. New value = '");
      message.append(modificationEvent.valueToBe());
      message.append("'.");
      text = message.toString();
    }
    else if (event instanceof BreakpointEvent) {
      BreakpointEvent breakpointEvent = (BreakpointEvent)event;
      Breakpoint breakpoint = descriptor.getFirst();
      if (breakpoint instanceof LineBreakpoint && !((LineBreakpoint)breakpoint).isVisible()) {
        text = "Stopped at cursor";
      }
      else {
        Location location = breakpointEvent.location();
        try {
          text = "Breakpoint reached in " + location.sourceName() + "; at line "+location.lineNumber();
        }
        catch (AbsentInformationException e) {
          text = "Breakpoint reached";
        }
        catch (InternalException e) {
          text = "Breakpoint reached";          
        }
      }
    }
    else if (event instanceof MethodEntryEvent) {
      MethodEntryEvent entryEvent = (MethodEntryEvent)event;
      Method method = entryEvent.method();
      text = "Method '" + method + "' entered";
    }
    else if (event instanceof MethodExitEvent) {
      MethodExitEvent exitEvent = (MethodExitEvent)event;
      Method method = exitEvent.method();
      text = "Method breakpoint reached. Method '" + method + "' is about to exit";
    }
    return text;
  }

  private class DebuggerEventThread extends Thread {
    final VirtualMachineProxyImpl myVmProxy;

    DebuggerEventThread () {
      super("DebuggerEventThread");
      myVmProxy = getVirtualMachineProxy();
    }

    private boolean myIsStopped = false;

    public synchronized void stopListening() {
      myIsStopped = true;
    }

    private synchronized boolean isStopped() {
      return myIsStopped;
    }

    public void run() {
      try {
        EventQueue eventQueue = myVmProxy.eventQueue();
        while (!isStopped()) {
          try {
            if (LOG.isDebugEnabled()) {
              LOG.debug("Listening events");
            }
            final EventSet eventSet = eventQueue.remove();

            if (LOG.isDebugEnabled()) {
              LOG.debug("EventSet " + eventSet.toString() + ", suspendPolicy=" + eventSet.suspendPolicy() + ";size=" + eventSet.size());
            }

            DebugProcessEvents.this.getManagerThread().invokeAndWait(new DebuggerCommandImpl() {
              protected void action() throws Exception {
                final SuspendContextImpl suspendContext = getSuspendManager().pushSuspendContext(eventSet);

                for (EventIterator eventIterator = eventSet.eventIterator(); eventIterator.hasNext(); ) {
                  final Event event = eventIterator.nextEvent();

                  if (LOG.isDebugEnabled()) {
                    LOG.debug("Event : " + event);
                  }
                  try {
                    if (event instanceof VMStartEvent) {
                      //Sun WTK fails when J2ME when event set is resumed on VMStartEvent
                      processVMStartEvent(suspendContext, (VMStartEvent)event);
                    }
                    else if (event instanceof VMDeathEvent) {
                      processVMDeathEvent(suspendContext, event);
                    }
                    else if (event instanceof VMDisconnectEvent) {
                      processVMDeathEvent(suspendContext, event);
                    }
                    else if (event instanceof ClassPrepareEvent) {
                      processClassPrepareEvent(suspendContext, (ClassPrepareEvent)event);
                    }
                    //AccessWatchpointEvent, BreakpointEvent, ExceptionEvent, MethodEntryEvent, MethodExitEvent,
                    //ModificationWatchpointEvent, StepEvent, WatchpointEvent
                    else if (event instanceof StepEvent) {
                      processStepEvent(suspendContext, (StepEvent)event);
                    }
                    else if (event instanceof LocatableEvent) {
                      processLocatableEvent(suspendContext, (LocatableEvent)event);
                    }
                    else if (event instanceof ClassUnloadEvent){
                      processDefaultEvent(suspendContext);
                    }
                  }
                  catch (VMDisconnectedException e) {
                    LOG.debug(e);
                  }
                  catch (Throwable e) {
                    LOG.error(e);
                  }
                }
              }
            });

          }
          catch (InternalException e) {
            LOG.debug(e);
          }
          catch (InterruptedException e) {
            throw e;
          }
          catch (VMDisconnectedException e) {
            throw e;
          }
          catch (Throwable e) {
            LOG.debug(e);
          }
        }
      }
      catch (InterruptedException e) {
        invokeVMDeathEvent();
      }
      catch (VMDisconnectedException e) {
        invokeVMDeathEvent();
      }
    }

    private void invokeVMDeathEvent() {
      DebugProcessEvents.this.getManagerThread().invokeAndWait(new DebuggerCommandImpl() {
        protected void action() throws Exception {
          SuspendContextImpl suspendContext = getSuspendManager().pushSuspendContext(EventRequest.SUSPEND_NONE, 1);
          processVMDeathEvent(suspendContext, null);
        }
      });
    }
  }

  private static void preprocessEvent(SuspendContextImpl suspendContext, ThreadReference thread) {
    ThreadReferenceProxyImpl oldThread = suspendContext.getThread();
    suspendContext.setThread(thread);

    if(oldThread == null) {
      //this is the first event in the eventSet that we process
      suspendContext.getDebugProcess().beforeSuspend(suspendContext);
    }
  }

  private void processVMStartEvent(final SuspendContextImpl suspendContext, VMStartEvent event) {
    preprocessEvent(suspendContext, event.thread());

    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: processVMStartEvent()");
    }

    showStatusText(this, event);

    getSuspendManager().voteResume(suspendContext);
  }

  private void vmAttached() {
    LOG.assertTrue(!isAttached());
    if(isDetached()) return;

    setIsAttached();

    myDebugProcessDispatcher.getMulticaster().processAttached(this);

    showStatusText("Connected");
    if (LOG.isDebugEnabled()) {
      LOG.debug("leave: processVMStartEvent()");
    }
  }

  private void processVMDeathEvent(SuspendContextImpl suspendContext, Event event) {
    preprocessEvent(suspendContext, null);

    if (myEventThread != null) {
      myEventThread.stopListening();
      myEventThread = null;
    }

    cancelRunToCursorBreakpoint();

    closeProcess(true);

    if(event != null) {
      showStatusText(this, event);
    }
  }

  private void processClassPrepareEvent(SuspendContextImpl suspendContext, ClassPrepareEvent event) {
    preprocessEvent(suspendContext, event.thread());
    if (LOG.isDebugEnabled()) {
      LOG.debug("Class prepared: " + event.referenceType().name());
    }
    suspendContext.getDebugProcess().getRequestsManager().processClassPrepared(event);

    getSuspendManager().voteResume(suspendContext);
  }

  private void processStepEvent(SuspendContextImpl suspendContext, StepEvent event) {
    ThreadReference thread = event.thread();
    LOG.assertTrue(thread.isSuspended());
    preprocessEvent(suspendContext, thread);

    RequestHint hint = (RequestHint)event.request().getProperty("hint");

    boolean shouldResume = false;

    if (hint != null) {
      if (hint.shouldSkipFrame(suspendContext)) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("STEPOUT doStep");
        }
        shouldResume = doStep(suspendContext.getThread(), hint.getDepth(), hint);
      }

      if(!shouldResume && hint.isRestoreBreakpoints()) {
        DebuggerManagerEx.getInstanceEx(getProject()).getBreakpointManager().enableBreakpoints(DebugProcessEvents.this);
      }
    }

    if(shouldResume) {
      getSuspendManager().voteResume(suspendContext);
    } else {
      showStatusText("");
      getSuspendManager().voteSuspend(suspendContext);
    }
  }

  private void processLocatableEvent(final SuspendContextImpl suspendContext, final LocatableEvent event) {
    ThreadReference thread = event.thread();
    LOG.assertTrue(thread.isSuspended());
    preprocessEvent(suspendContext, thread);

    //we use invokeLater to allow processing events during processing this one
    //this is especially nesessary if a method is breakpoint condition
    getManagerThread().invokeLater(new SuspendContextCommandImpl(suspendContext) {
      public void contextAction() throws Exception {
        final SuspendManager suspendManager = getSuspendManager();
        SuspendContextImpl evaluatingContext = SuspendManagerUtil.getEvaluatingContext(suspendManager, getSuspendContext().getThread());

        if(evaluatingContext != null && !evaluatingContext.getEvaluationContext().isAllowBreakpoints()) {
          suspendManager.voteResume(suspendContext);
          return;
        }

        LocatableEventRequestor requestor = (LocatableEventRequestor) getRequestsManager().findRequestor(event.request());

        boolean shouldResumeExecution = true;

        if(requestor != null) {
          shouldResumeExecution = requestor.processLocatableEvent(this, event);
        }

        if(shouldResumeExecution) {
          suspendManager.voteResume(suspendContext);
        }
        else {
          //suspendContext.voteResume();
          suspendManager.voteSuspend(suspendContext);
          showStatusText(DebugProcessEvents.this, event);
        }
      }
    });
    //suspendContext.voteResume();
  }

  private void processDefaultEvent(SuspendContextImpl suspendContext) {
    preprocessEvent(suspendContext, null);
    getSuspendManager().voteResume(suspendContext);
  }
}
