package com.intellij.debugger.impl;

import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.*;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationListener;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.debugger.ui.breakpoints.BreakpointWithHighlighter;
import com.intellij.debugger.ui.breakpoints.LineBreakpoint;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RemoteState;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiDocumentManager;
import com.sun.jdi.request.EventRequest;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class DebuggerSession {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.impl.DebuggerSession");
  // flags
  private final MyDebuggerStateManager myContextManager;

  public static final int STATE_STOPPED = 0;
  public static final int STATE_RUNNING = 1;
  public static final int STATE_WAITING_ATTACH = 2;
  public static final int STATE_PAUSED = 3;
  public static final int STATE_WAIT_EVALUATION = 5;
  public static final int STATE_DISPOSED = 6;

  public static final int EVENT_ATTACHED = 0;
  public static final int EVENT_DETACHED = 1;
  public static final int EVENT_RESUME = 4;
  public static final int EVENT_STEP = 5;
  public static final int EVENT_PAUSE = 6;
  public static final int EVENT_REFRESH = 7;
  public static final int EVENT_CONTEXT = 8;
  public static final int EVENT_START_WAIT_ATTACH = 9;
  public static final int EVENT_DISPOSE = 10;

  private boolean myIsEvaluating;

  private DebuggerSessionState myState = null;

  private static final String MSG_WAITING_ATTACH = "Debugger is waiting for application to start";
  private static final String MSG_LISTENING = "Listening to the connection";
  private static final String MSG_CONNECTING = "Connecting to the target VM";
  private static final String MSG_RUNNING = "The application is running";
  private static final String MSG_CONNECTED = "Connected to the target VM";
  private static final String MSG_STOPPED = "Debugger disconnected";
  private static final String MSG_DISCONNECTED = "Disconnected from the target VM";
  private static final String MSG_WAIT_EVALUATION = "Waiting until last debugger command completes";

  private final String mySessionName;
  private final DebugProcessImpl myDebugProcess;

  private final DebuggerContextImpl SESSION_EMPTY_CONTEXT;
  //Thread, user is currently stepping through
  private Set<ThreadReferenceProxyImpl> mySteppingThroughThreads = new HashSet<ThreadReferenceProxyImpl>();

  public boolean isSteppingThrough(ThreadReferenceProxyImpl threadProxy) {
    return mySteppingThroughThreads.contains(threadProxy);
  }


  private class MyDebuggerStateManager extends DebuggerStateManager {
    private DebuggerContextImpl myDebuggerContext;

    MyDebuggerStateManager() {
      myDebuggerContext = SESSION_EMPTY_CONTEXT;
    }

    public DebuggerContextImpl getContext() {
      return myDebuggerContext;
    }

    /**
     * actually state changes not in the same sequence as you call setState
     * the 'resuming' setState with context.getSuspendContext() == null may be set prior to
     * the setState for the context with context.getSuspendContext()
     *
     * in this case we assume that the latter setState is ignored
     * since the thread was resumed
     */
    public void setState(final DebuggerContextImpl context, final int state, final int event, final String description) {
      LOG.assertTrue(SwingUtilities.isEventDispatchThread());
      LOG.assertTrue(context.getDebuggerSession() == DebuggerSession.this || context.getDebuggerSession() == null);
      final Runnable setStateRunnable = new Runnable() {
        public void run() {
          LOG.assertTrue(myDebuggerContext.isInitialised());
          myDebuggerContext = context;
          if (LOG.isDebugEnabled()) {
            LOG.debug("DebuggerSession state = " + state + ", event = " + event);
          }

          myIsEvaluating = false;

          myState = new DebuggerSessionState(state, description);
          fireStateChanged(context, event);
        }
      };

      if(context.getSuspendContext() == null) {
        setStateRunnable.run();
      }
      else {
        getProcess().getManagerThread().invokeLater(new DebuggerContextCommandImpl(context) {
          public void threadAction() {
            context.initCaches();
            DebuggerInvocationUtil.invokeLater(getProject(), setStateRunnable);
          }
        });
      }
    }
  }

  protected DebuggerSession(String sessionName, final DebugProcessImpl debugProcess) {
    mySessionName  = sessionName;
    myDebugProcess = debugProcess;
    SESSION_EMPTY_CONTEXT = DebuggerContextImpl.createDebuggerContext(DebuggerSession.this, null, null, null);
    myContextManager = new MyDebuggerStateManager();
    myState = new DebuggerSessionState(STATE_STOPPED, null);
    myDebugProcess.addDebugProcessListener(new DebugProcessAdapterImpl() {
      //executed in manager thread
      public void connectorIsReady() {
        DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
          public void run() {
            RemoteConnection connection = myDebugProcess.getConnection();
            final String connectionStatusMessage = DebugProcessImpl.createConnectionStatusMessage(
              connection.isServerMode() ? DebuggerSession.MSG_LISTENING : DebuggerSession.MSG_CONNECTING, connection
            );
            getContextManager().setState(SESSION_EMPTY_CONTEXT, DebuggerSession.STATE_WAITING_ATTACH, DebuggerSession.EVENT_START_WAIT_ATTACH, connectionStatusMessage);
          }
        });
      }

      public void paused(final SuspendContextImpl suspendContext) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("paused");
        }

        ThreadReferenceProxyImpl currentThread   = suspendContext.getThread();
        final StackFrameContext        positionContext;

        if (currentThread == null) {
          //Pause pressed
          LOG.assertTrue(suspendContext.getSuspendPolicy() == EventRequest.SUSPEND_ALL);
          SuspendContextImpl oldContext = getProcess().getSuspendManager().getPausedContext();

          if (oldContext != null) {
            currentThread = oldContext.getThread();
          }

          if(currentThread == null) {
            currentThread = getProcess().getVirtualMachineProxy().allThreads().iterator().next();
          }


          StackFrameProxyImpl proxy;
          try {
            proxy = (currentThread.frameCount() > 0) ? currentThread.frame(0) : null;
          }
          catch (EvaluateException e) {
            proxy = null;
            LOG.error(e);
          }
          positionContext = new SimpleStackFrameContext(proxy, debugProcess);
        }
        else {
          positionContext = suspendContext;
        }

        final SourcePosition position = PsiDocumentManager.getInstance(getProject()).commitAndRunReadAction(new Computable<SourcePosition>() {
          public SourcePosition compute() {
            return ContextUtil.getSourcePosition(positionContext);
          }
        });

        if (position != null) {
          ArrayList<LineBreakpoint> toDelete = new ArrayList<LineBreakpoint>();

          java.util.List<Pair<Breakpoint, com.sun.jdi.event.Event>> eventDescriptors = DebuggerUtilsEx.getEventDescriptors(suspendContext);
          for (Iterator<Pair<Breakpoint, com.sun.jdi.event.Event>> iterator = eventDescriptors.iterator(); iterator.hasNext();) {
            Pair<Breakpoint, com.sun.jdi.event.Event> eventDescriptor = iterator.next();
            Breakpoint breakpoint = eventDescriptor.getFirst();
            if (breakpoint instanceof LineBreakpoint) {
              SourcePosition sourcePosition = ((BreakpointWithHighlighter)breakpoint).getSourcePosition();
              if (sourcePosition == null || sourcePosition.getLine() != position.getLine()) {
                toDelete.add((LineBreakpoint)breakpoint);
              }
            }
          }

          RequestManagerImpl requestsManager = suspendContext.getDebugProcess().getRequestsManager();
          for (Iterator<LineBreakpoint> iterator = toDelete.iterator(); iterator.hasNext();) {
            BreakpointWithHighlighter breakpointWithHighlighter = iterator.next();
            requestsManager.deleteRequest(breakpointWithHighlighter);
            requestsManager.setInvalid(breakpointWithHighlighter, "Source code changed");
            breakpointWithHighlighter.updateUI();
          }

          if (toDelete.size() > 0 && toDelete.size() == eventDescriptors.size()) {
            getProcess().getManagerThread().invokeLater(myDebugProcess.createResumeCommand(suspendContext));
            return;
          }
        }

        final DebuggerContextImpl debuggerContext = DebuggerContextImpl.createDebuggerContext(DebuggerSession.this, suspendContext, currentThread, null);
        debuggerContext.setPositionCache(position);

        DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
          public void run() {
            getContextManager().setState(debuggerContext, STATE_PAUSED, EVENT_PAUSE, null);
          }
        });
      }

      public void resumed(final SuspendContextImpl suspendContext) {
        final SuspendContextImpl currentContext = getProcess().getSuspendManager().getPausedContext();
        DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
          public void run() {
            if (currentContext != null) {
              getContextManager().setState(DebuggerContextUtil.createDebuggerContext(DebuggerSession.this, currentContext), STATE_PAUSED, EVENT_REFRESH, null);
            }
            else {
              getContextManager().setState(SESSION_EMPTY_CONTEXT, STATE_RUNNING, EVENT_REFRESH, null);
            }
          }
        });
      }

      public void processAttached(final DebugProcessImpl process) {
        final String message = DebugProcessImpl.createConnectionStatusMessage(MSG_CONNECTED, getProcess().getConnection());

        process.getExecutionResult().getProcessHandler().notifyTextAvailable(message + "\n", ProcessOutputTypes.SYSTEM);
        DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
          public void run() {
            getContextManager().setState(SESSION_EMPTY_CONTEXT, STATE_RUNNING, EVENT_ATTACHED, message);
          }
        });
      }

      public void attachException(final RunProfileState state, final ExecutionException exception, final RemoteConnection remoteConnection) {
        DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
          public void run() {
            String message = "";
            if (state instanceof RemoteState) {
              message = DebugProcessImpl.createConnectionStatusMessage(DebugProcessImpl.MSG_FAILD_TO_CONNECT, remoteConnection);
            }
            message += exception.getMessage();
            getContextManager().setState(SESSION_EMPTY_CONTEXT, STATE_STOPPED, EVENT_DETACHED, message);
          }
        });
      }

      public void processDetached(final DebugProcessImpl debugProcess) {
        ExecutionResult executionResult = debugProcess.getExecutionResult();
        if(executionResult != null) {
          executionResult.getProcessHandler().notifyTextAvailable(DebugProcessImpl.createConnectionStatusMessage(MSG_DISCONNECTED, getProcess().getConnection()) + "\n", ProcessOutputTypes.SYSTEM);
        }
        DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
          public void run() {
            getContextManager().setState(SESSION_EMPTY_CONTEXT, STATE_STOPPED, EVENT_DETACHED, MSG_DISCONNECTED);
          }
        });
        mySteppingThroughThreads.clear();
      }
    });

    myDebugProcess.addEvaluationListener(new EvaluationListener() {
      public void evaluationStarted(SuspendContextImpl context) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            myIsEvaluating = true;
          }
        });
      }

      public void evaluationFinished(final SuspendContextImpl context) {
        DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
          public void run() {
            myIsEvaluating = false;
            if (context != getSuspendContext()) {
              getContextManager().setState(DebuggerContextUtil.createDebuggerContext(DebuggerSession.this, context), STATE_PAUSED, EVENT_REFRESH, null);
            }
          }
        });
      }
    });
  }

  public DebuggerStateManager getContextManager() {
    return myContextManager;
  }

  public Project getProject() {
    return getProcess().getProject();
  }

  public String getSessionName() {
    return mySessionName;
  }

  public DebugProcessImpl getProcess() {
    return myDebugProcess;
  }

  private static class DebuggerSessionState {
    final int myState;
    final String myDescription;

    public DebuggerSessionState(int state, String description) {
      myState = state;
      myDescription = description;
    }
  }

  private DebuggerSessionState getSessionState() {
    return myState;
  }

  public int getState() {
    return getSessionState().myState;
  }

  public String getStateDescription() {
    DebuggerSessionState state = getSessionState();
    if (state.myDescription != null) return state.myDescription;

    switch (state.myState) {
      case STATE_STOPPED:
        return MSG_STOPPED;
      case STATE_RUNNING:
        return MSG_RUNNING;
      case STATE_WAITING_ATTACH:
        RemoteConnection connection = getProcess().getConnection();
        return DebugProcessImpl.createConnectionStatusMessage(connection.isServerMode() ? MSG_LISTENING : MSG_CONNECTING, connection);
      case STATE_PAUSED:
        return "Paused";
      case STATE_WAIT_EVALUATION:
        return MSG_WAIT_EVALUATION;
      case STATE_DISPOSED:
        return MSG_STOPPED;
    }
    return state.myDescription;
  }

  /* Stepping */
  private void resumeAction(final SuspendContextCommandImpl action, int event) {
    getContextManager().setState(SESSION_EMPTY_CONTEXT, STATE_WAIT_EVALUATION, event, null);
    myDebugProcess.getManagerThread().invokeLater(action, DebuggerManagerThreadImpl.HIGH_PRIORITY);
  }

  public void stepOut() {
    mySteppingThroughThreads.add(getSuspendContext().getThread());
    resumeAction(myDebugProcess.createStepOutCommand(getSuspendContext()), EVENT_STEP);
  }

  public void stepOver(boolean ignoreBreakpoints) {
    mySteppingThroughThreads.add(getSuspendContext().getThread());
    resumeAction(myDebugProcess.createStepOverCommand(getSuspendContext(), ignoreBreakpoints), EVENT_STEP);
  }

  public void stepInto(final boolean ignoreFilters) {
    mySteppingThroughThreads.add(getSuspendContext().getThread());
    resumeAction(myDebugProcess.createStepIntoCommand(getSuspendContext(), ignoreFilters), EVENT_STEP);
  }

  public void runToCursor(Document document, int line) {
    try {
      SuspendContextCommandImpl runToCursorCommand = myDebugProcess.createRunToCursorCommand(getSuspendContext(), document, line);
      mySteppingThroughThreads.add(getSuspendContext().getThread());
      resumeAction(runToCursorCommand, EVENT_STEP);
    }
    catch (EvaluateException e) {
      Messages.showErrorDialog(e.getMessage(), "Cannot Run to Cursor");
    }
  }


  public void resume() {
    if(getSuspendContext() != null) {
      mySteppingThroughThreads.remove(getSuspendContext().getThread());
      resumeAction(myDebugProcess.createResumeCommand(getSuspendContext()), EVENT_RESUME);
    }
  }

  public void pause() {
    myDebugProcess.getManagerThread().invokeLater(myDebugProcess.createPauseCommand());
  }

  /*Presentation*/

  public void showExecutionPoint() {
    getContextManager().setState(DebuggerContextUtil.createDebuggerContext(DebuggerSession.this, getSuspendContext()), STATE_PAUSED, EVENT_REFRESH, null);
  }

  public void refresh() {
    if (getState() == DebuggerSession.STATE_PAUSED) {
      DebuggerContextImpl context = myContextManager.getContext();
      DebuggerContextImpl newContext = DebuggerContextImpl.createDebuggerContext(this, context.getSuspendContext(), context.getThreadProxy(), context.getFrameProxy());
      myContextManager.setState(newContext, DebuggerSession.STATE_PAUSED, EVENT_REFRESH, null);
    }
  }

  public void dispose() {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    getProcess().dispose();
    getContextManager().setState(SESSION_EMPTY_CONTEXT, STATE_DISPOSED, EVENT_DISPOSE, null);
  }

  // ManagerCommands
  public boolean isStopped() {
    return getState() == STATE_STOPPED;
  }

  public boolean isAttached() {
    return !isStopped() && getState() != STATE_WAITING_ATTACH;
  }

  public boolean isPaused() {
    return getState() == STATE_PAUSED;
  }

  public boolean isConnecting() {
    return getState() == STATE_WAITING_ATTACH;
  }

  public boolean isEvaluating() {
    return myIsEvaluating;
  }

  public boolean isRunning() {
    return getState() == STATE_RUNNING && !getProcess().getExecutionResult().getProcessHandler().isProcessTerminated();
  }

  private SuspendContextImpl getSuspendContext() {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    return getContextManager().getContext().getSuspendContext();
  }

  protected ExecutionResult attach(final RunProfileState state, final RemoteConnection remoteConnection, final boolean pollConnection) throws ExecutionException {
    final ExecutionResult executionResult = myDebugProcess.attachVirtualMachine(state, remoteConnection, pollConnection);
    getContextManager().setState(SESSION_EMPTY_CONTEXT, STATE_WAITING_ATTACH, EVENT_START_WAIT_ATTACH, DebugProcessImpl.createConnectionStatusMessage(MSG_WAITING_ATTACH, remoteConnection));
    return executionResult;
  }
}