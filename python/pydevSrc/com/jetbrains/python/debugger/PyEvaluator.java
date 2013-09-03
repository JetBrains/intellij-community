package com.jetbrains.python.debugger;

import com.intellij.xdebugger.frame.XValueChildrenList;
import org.jetbrains.annotations.Nullable;

/**
 * @author traff
 */
public interface PyEvaluator {
  PyDebugValue evaluate(final String expression, final boolean execute, boolean doTrunc) throws PyDebuggerException;

  @Nullable
  XValueChildrenList loadFrame() throws PyDebuggerException;
}
