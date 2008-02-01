package com.intellij.xdebugger.evaluation;

import org.jetbrains.annotations.NotNull;
import com.intellij.xdebugger.frame.XValue;

/**
 * @author nik
 */
public abstract class XDebuggerEvaluator {

  public abstract boolean evaluateCondition(@NotNull String expression);

  public abstract String evaluateMessage(@NotNull String expression);

  public abstract XValue evaluate(@NotNull String expression);
}
