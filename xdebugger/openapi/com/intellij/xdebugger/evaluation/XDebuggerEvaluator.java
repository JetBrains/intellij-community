package com.intellij.xdebugger.evaluation;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.xdebugger.frame.XValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  /**
   * Return text range of expression which can be evaluated.
   * @param project project
   * @param document document
   * @param offset offset
   * @return text range of expression
   */
  @Nullable
  public abstract TextRange getExpressionRangeAtOffset(final Project project, final Document document, int offset);

  /**
   * @return delay before showing value tooltip (in ms)
   */
  public int getValuePopupDelay() {
    return 700;
  }

  public interface XEvaluationCallback {

    void evaluated(@NotNull XValue result);

    void errorOccured(@NotNull String errorMessage);

  }
}
