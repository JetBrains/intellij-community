package com.jetbrains.python.debugger;

import com.intellij.xdebugger.frame.XFullValueEvaluator;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class PyFullValueEvaluator extends XFullValueEvaluator {
  private final PyFrameAccessor myDebugProcess;
  private final String myExpression;

  /**
   * @param linkText     text of the link what will be appended to a variables tree node text
   * @param debugProcess
   * @param expression
   */
  protected PyFullValueEvaluator(PyFrameAccessor debugProcess, String expression) {
    myDebugProcess = debugProcess;
    myExpression = expression;
  }


  @Override
  public void startEvaluation(@NotNull XFullValueEvaluationCallback callback) {
    String expression = myExpression.trim();
    if ("".equals(expression)) {
      callback.evaluated("");
      return;
    }

    try {
      final PyDebugValue value = myDebugProcess.evaluate(expression, false, false);
      callback.evaluated(value.getValue());
    }
    catch (PyDebuggerException e) {
      callback.errorOccurred(e.getTracebackError());
    }
  }
}
