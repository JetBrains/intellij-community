package com.intellij.debugger.engine.events;

import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.SuspendManager;
import com.intellij.debugger.engine.SuspendManagerUtil;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.openapi.diagnostic.Logger;

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

  public final DebuggerContextImpl getDebuggerContext() {
    return myDebuggerContext;
  }

  public final void contextAction() throws Exception {
    final SuspendManager suspendManager = myDebuggerContext.getDebugProcess().getSuspendManager();

    if (SuspendManagerUtil.hasSuspendingContexts(suspendManager, myDebuggerContext.getThreadProxy())) {
      final SuspendContextImpl suspendContext = getSuspendContext();
      if (!suspendContext.isResumed()) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Context thread " + suspendContext.getThread());
          LOG.debug("Debug thread" + myDebuggerContext.getThreadProxy());
        }
        threadAction();
      }
    }
    else {
      // there are no suspend context currently registered
      SuspendContextImpl threadContext = SuspendManagerUtil.findContextByThread(suspendManager, myDebuggerContext.getThreadProxy());
      if(threadContext != null) {
        SuspendManagerUtil.postponeCommand(threadContext, this);
      }
      else {
        notifyCancelled();
      }
    }
  }

  abstract public void threadAction ();
}
