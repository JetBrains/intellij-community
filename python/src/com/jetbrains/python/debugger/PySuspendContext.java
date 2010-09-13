package com.jetbrains.python.debugger;

import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XSuspendContext;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;


public class PySuspendContext extends XSuspendContext {

  private final XExecutionStack myActiveStack;
  private PyDebugProcess myDebugProcess;

  public PySuspendContext(@NotNull final PyDebugProcess debugProcess, @NotNull final PyThreadInfo threadInfo) {
    myDebugProcess = debugProcess;
    myActiveStack = new PyExecutionStack(debugProcess, threadInfo);
  }

  @Override
  public XExecutionStack getActiveExecutionStack() {
    return myActiveStack;
  }

  @Override
  public XExecutionStack[] getExecutionStacks() {
    final Collection<PyThreadInfo> threads = myDebugProcess.getThreads();
    if (threads.size() < 1) {
      return XExecutionStack.EMPTY_ARRAY;
    }
    else {
      XExecutionStack[] stacks = new XExecutionStack[threads.size()];
      int i = 0;
      for (PyThreadInfo thread : threads) {
        stacks[i++] = new PyExecutionStack(myDebugProcess, thread);
      }
      return stacks;
    }
  }

}
