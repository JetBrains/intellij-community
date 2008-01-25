package com.intellij.xdebugger.evaluation;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class XDebuggerEvaluator {

  public abstract boolean evaluateCondition(@NotNull String expression);

  public abstract String evaluateMessage(@NotNull String expression);
}
