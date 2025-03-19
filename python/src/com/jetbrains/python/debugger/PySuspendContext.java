// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger;

import com.intellij.icons.AllIcons;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.jetbrains.python.debugger.pydev.AbstractCommand;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;


public class PySuspendContext extends XSuspendContext {

  private final PyExecutionStack myActiveStack;
  private final PyDebugProcess myDebugProcess;

  public PySuspendContext(final @NotNull PyDebugProcess debugProcess, final @NotNull PyThreadInfo threadInfo) {
    myDebugProcess = debugProcess;
    myActiveStack = new PyExecutionStack(debugProcess, threadInfo, getThreadIcon(threadInfo));
  }

  @Override
  public @NotNull PyExecutionStack getActiveExecutionStack() {
    return myActiveStack;
  }

  public static @NotNull Icon getThreadIcon(@NotNull PyThreadInfo threadInfo) {
    if ((threadInfo.getState() == PyThreadInfo.State.SUSPENDED) && (threadInfo.getStopReason() == AbstractCommand.SET_BREAKPOINT)) {
      return AllIcons.Debugger.ThreadAtBreakpoint;
    }
    else {
      return AllIcons.Debugger.ThreadSuspended;
    }
  }

  @Override
  public XExecutionStack @NotNull [] getExecutionStacks() {
    final Collection<PyThreadInfo> threads = myDebugProcess.getThreads();
    if (threads.isEmpty()) {
      return XExecutionStack.EMPTY_ARRAY;
    }
    else {
      XExecutionStack[] stacks = new XExecutionStack[threads.size()];
      int i = 0;
      for (PyThreadInfo thread : threads) {
        stacks[i++] = new PyExecutionStack(myDebugProcess, thread, getThreadIcon(thread));
      }
      return stacks;
    }
  }

}
