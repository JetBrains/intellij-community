package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.SuspendManager;
import com.intellij.debugger.engine.SuspendManagerUtil;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.ThreadGroupReferenceProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
import com.intellij.debugger.ui.tree.ThreadDescriptor;
import com.intellij.openapi.util.IconLoader;
import com.sun.jdi.ThreadReference;

import javax.swing.*;

public class ThreadDescriptorImpl extends NodeDescriptorImpl implements ThreadDescriptor{
  private final ThreadReferenceProxyImpl myThread;
  private String myName = null;
  private boolean myIsExpandable   = true;
  private boolean myIsSuspended    = false;
  private boolean myIsCurrent;
  private boolean myIsFrozen;

  private boolean            myIsAtBreakpoint;
  private SuspendContextImpl mySuspendContext;

  private static Icon myRunningThreadIcon = IconLoader.getIcon("/debugger/threadRunning.png");
  private static Icon myCurrentThreadIcon = IconLoader.getIcon("/debugger/threadCurrent.png");
  private static Icon myThreadAtBreakpointIcon = IconLoader.getIcon("/debugger/threadAtBreakpoint.png");
  private static Icon myFrozenThreadIcon = IconLoader.getIcon("/debugger/threadFrozen.png");
  private static Icon mySuspendedThreadIcon = IconLoader.getIcon("/debugger/threadSuspended.png");

  public ThreadDescriptorImpl(ThreadReferenceProxyImpl thread) {
    myThread = thread;
  }

  public String getName() {
    return myName;
  }

  protected String calcRepresentation(EvaluationContextImpl context, DescriptorLabelListener labelListener) throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    ThreadReferenceProxyImpl thread = getThreadReference();
    if (thread.isCollected()) {
      return myName != null ? "Thread \"" + myName + "\" is garbage-collected" : "";
    }

    myName = thread.name();
    String label = "Thread \"" + myName + "\"@" + thread.uniqueID();

    ThreadGroupReferenceProxyImpl gr = getThreadReference().threadGroupProxy();
    String grname = (gr != null)? gr.name() : null;

    if (grname != null && !"SYSTEM".equals(grname.toUpperCase())) {
      label += " in group \"" + grname + "\"";
    }

    label += " status: " + DebuggerUtilsEx.getThreadStatusText(getThreadReference().status());

    return label;
  }

  public ThreadReferenceProxyImpl getThreadReference() {
    return myThread;
  }

  public boolean isCurrent() {
    return myIsCurrent;
  }

  public boolean isFrozen() {
    return myIsFrozen;
  }

  private boolean calcIsCurrent(EvaluationContextImpl evaluationContext) {
    ThreadReferenceProxyImpl currentThread = evaluationContext.getSuspendContext().getThread();
    return currentThread == getThreadReference();
  }

  public boolean isExpandable() {
    return myIsExpandable;
  }

  public void setContext(EvaluationContextImpl context) {
    ThreadReferenceProxyImpl thread = getThreadReference();

    SuspendManager suspendManager = context.getDebugProcess().getSuspendManager();
    myIsSuspended    = suspendManager.isSuspended(thread);
    myIsExpandable   = calcExpandable(suspendManager);
    SuspendContextImpl threadContext = SuspendManagerUtil.findContextByThread(suspendManager, thread);
    mySuspendContext = SuspendManagerUtil.getSuspendContextForThread(context.getSuspendContext(), thread);
    myIsAtBreakpoint = threadContext != null;
    myIsCurrent      = calcIsCurrent(context);
    myIsFrozen       = suspendManager.isFrozen(getThreadReference());
  }

  private boolean calcExpandable(SuspendManager manager) {
    ThreadReferenceProxyImpl threadProxy = getThreadReference();
    if (threadProxy.isCollected() || !manager.isSuspended(myThread)) {
      return false;
    }
    int status = threadProxy.status();
    if (status == ThreadReference.THREAD_STATUS_UNKNOWN ||
        status == ThreadReference.THREAD_STATUS_NOT_STARTED ||
        status == ThreadReference.THREAD_STATUS_ZOMBIE) {
      return false;
    }
    try {
      return threadProxy.frameCount() > 0;
    }
    catch (EvaluateException e) {
      //LOG.assertTrue(false);
      // if we pause during evaluation of this method the exception is thrown
      //  private static void longMethod(){
      //    try {
      //      Thread.sleep(100000);
      //    } catch (InterruptedException e) {
      //      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
      //    }
      //  }
      return false;
    }
  }

  public SuspendContextImpl getSuspendContext() {
    return mySuspendContext;
  }

  public boolean isAtBreakpoint() {
    return myIsAtBreakpoint;
  }

  public boolean isSuspended() {
    return myIsSuspended;
  }

  public Icon getIcon() {
    Icon nodeIcon;
    if(isCurrent()) {
      nodeIcon = myCurrentThreadIcon;
    }
    else if(isFrozen()) {
      nodeIcon = myFrozenThreadIcon;
    }
    else if(isAtBreakpoint()) {
      nodeIcon = myThreadAtBreakpointIcon;
    }
    else if(isSuspended()) {
      nodeIcon = mySuspendedThreadIcon;
    } else {
      nodeIcon = myRunningThreadIcon;
    }
    return nodeIcon;
  }
}