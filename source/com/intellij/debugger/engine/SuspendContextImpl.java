/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.engine;

import com.intellij.Patches;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.HashSet;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.request.EventRequest;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Feb 24, 2004
 * Time: 8:04:50 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class SuspendContextImpl implements SuspendContext {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.SuspendContextImpl");

  private final DebugProcessImpl myDebugProcess;
  private final int mySuspendPolicy;

  private ThreadReferenceProxyImpl myThread;
  boolean myIsVotedForResume = true;

  protected int myVotesToVote;
  protected Set<ThreadReferenceProxyImpl> myResumedThreads;

  private EventSet myEventSet;
  private boolean  myIsResumed;

  public List<SuspendContextCommandImpl> myPostponedCommands = new ArrayList<SuspendContextCommandImpl>();
  public boolean                         myInProgress;
  private HashSet<ObjectReference>       myKeptReferences = new HashSet<ObjectReference>();
  private EvaluationContextImpl          myEvaluationContext = null;

  SuspendContextImpl(DebugProcessImpl debugProcess, int suspendPolicy, int eventVotes, EventSet set) {
    LOG.assertTrue(debugProcess != null);
    myDebugProcess = debugProcess;
    mySuspendPolicy = suspendPolicy;
    myVotesToVote = eventVotes;
    myEventSet = set;
  }

  public void setThread(ThreadReference thread) {
    assertNotResumed();
    ThreadReferenceProxyImpl threadProxy = myDebugProcess.getVirtualMachineProxy().getThreadReferenceProxy(thread);
    LOG.assertTrue(myThread == null || myThread == threadProxy);
    myThread = threadProxy;
  }

  protected abstract void resumeImpl();

  protected void resume(){
    assertNotResumed();
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (!Patches.IBM_JDK_DISABLE_COLLECTION_BUG) {
      for (Iterator<ObjectReference> iterator = myKeptReferences.iterator(); iterator.hasNext();) {
        ObjectReference objectReference = iterator.next();
        objectReference.enableCollection();
      }
      myKeptReferences.clear();
    }
    resumeImpl();
    myIsResumed = true;
  }

  private void assertNotResumed() {
    LOG.assertTrue(!myIsResumed, "Cannot access SuspendContext. SuspendContext is resumed.");
  }


  public EventSet getEventSet() {
    assertNotResumed();
    return myEventSet;
  }

  public DebugProcessImpl getDebugProcess() {
    assertNotResumed();
    return myDebugProcess;
  }

  public StackFrameProxyImpl getFrameProxy() {
    assertNotResumed();
    try {
      return myThread != null && myThread.frameCount() > 0 ? myThread.frame(0) : null;
    }
    catch (EvaluateException e) {
      return null;
    }
  }

  public ThreadReferenceProxyImpl getThread() {
    return myThread;
  }

  public int getSuspendPolicy() {
    assertNotResumed();
    return mySuspendPolicy;
  }

  public void doNotResumeHack() {
    assertNotResumed();
    myVotesToVote = 1000000000;
  }

  public boolean isExplicitlyResumed(ThreadReferenceProxyImpl thread) {
    return myResumedThreads != null ? myResumedThreads.contains(thread) : false;
  }

  public boolean suspends(ThreadReferenceProxyImpl thread) {
    assertNotResumed();
    if(isEvaluating()) return false;
    switch(getSuspendPolicy()) {
      case EventRequest.SUSPEND_ALL:
        return !isExplicitlyResumed(thread);
      case EventRequest.SUSPEND_EVENT_THREAD:
        return thread == getThread();
    }
    return false;
  }

  public boolean isEvaluating() {
    assertNotResumed();
    return myEvaluationContext != null;
  }

  public EvaluationContextImpl getEvaluationContext() {
    return myEvaluationContext;
  }

  public boolean isResumed() {
    return myIsResumed;
  }

  public void setIsEvaluating(EvaluationContextImpl evaluationContext) {
    assertNotResumed();
    myEvaluationContext = evaluationContext;
  }

  public SuspendManager getSuspendManager() {
    return myDebugProcess.getSuspendManager();
  }

  public String toString() {
    if (myEventSet != null) {
      return myEventSet.toString();
    } else {
      return myThread != null ? myThread.toString() : DebuggerBundle.message("string.null.context");
    }
  }

  public void keep(ObjectReference reference) {
    if (!Patches.IBM_JDK_DISABLE_COLLECTION_BUG) {
      final boolean added = myKeptReferences.add(reference);
      if (added) {
        reference.disableCollection();
      }
    }
  }
}
