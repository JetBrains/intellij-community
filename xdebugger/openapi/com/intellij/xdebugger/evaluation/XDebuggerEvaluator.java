package com.intellij.xdebugger.evaluation;

import org.jetbrains.annotations.NotNull;
import com.intellij.xdebugger.frame.XValue;

/**
 * @author nik
 */
public abstract class XDebuggerEvaluator {

  /**
   * Evaluate <code>expression</code> to boolean
   * @param expression expression to evaluate
   * @return result
   */
  public abstract boolean evaluateCondition(@NotNull String expression);

  /**
   * Evaluate <code>expression</code> to string
   * @param expression expression to evaluate
   * @return result
   */
  public abstract String evaluateMessage(@NotNull String expression);

  /**
   * Start evaluating expression.
   * @param expression expression to evaluate
   * @param callback used to notify that the expression has been evaluated or an error occurs
   */
  public abstract void evaluate(@NotNull String expression, XEvaluationCallback callback);

  public static interface XEvaluationCallback {

    void evaluated(@NotNull XValue result);

    void errorOccured(@NotNull String errorMessage);

  }
}
