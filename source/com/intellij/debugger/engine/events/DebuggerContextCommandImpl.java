package com.intellij.debugger.engine.events;

import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.SuspendManagerUtil;
import com.intellij.debugger.engine.SuspendManager;
import com.intellij.openapi.diagnostic.Logger;

import java.util.List;
import java.util.Iterator;
import java.util.Set;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public abstract class DebuggerContextCommandImpl extends SuspendContextCommandImpl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.events.DebuggerContextCommandImpl");

  private final DebuggerContextImpl myDebuggerContext;

  protected DebuggerContextCommandImpl(DebuggerContextImpl debuggerContext) {
    super(debuggerContext.getSuspendContext());
    myDebuggerContext = debuggerContext;
  }

  public DebuggerContextImpl getDebuggerContext() {
    return myDebuggerContext;
  }

  public final void contextAction() throws Exception {
    SuspendManager suspendManager = myDebuggerContext.getDebugProcess().getSuspendManager();
    Set<SuspendContextImpl> suspendingContexts = SuspendManagerUtil.getSuspendingContexts(suspendManager, myDebuggerContext.getThreadProxy());

    if(suspendingContexts.isEmpty()) {
      SuspendContextImpl threadContext = SuspendManagerUtil.findContextByThread(suspendManager, myDebuggerContext.getThreadProxy());
      if(threadContext != null) {
        SuspendManagerUtil.postponeCommand(threadContext, this);
      }
      else {
        notifyCancelled();
      }
    }
    else {
      LOG.debug("Context thread " + getSuspendContext().getThread());
      LOG.debug("Debug thread" + myDebuggerContext.getThreadProxy());
      threadAction();
    }
  }

  abstract public void threadAction ();
}
