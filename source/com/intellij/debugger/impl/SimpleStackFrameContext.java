/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.impl;

import com.intellij.debugger.engine.StackFrameContext;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.jdi.StackFrameProxy;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 30, 2004
 */
public final class SimpleStackFrameContext implements StackFrameContext{
  private final StackFrameProxy myProxy;
  private final DebugProcess myProcess;

  public SimpleStackFrameContext(StackFrameProxy proxy, DebugProcess process) {
    myProxy = proxy;
    myProcess = process;
  }

  public StackFrameProxy getFrameProxy() {
    return myProxy;
  }

  public DebugProcess getDebugProcess() {
    return myProcess;
  }
}
