package com.jetbrains.python.debugger;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.xdebugger.frame.XFullValueEvaluator;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class PyFullValueEvaluator extends XFullValueEvaluator {
  @NotNull protected final PyFrameAccessor myDebugProcess;
  @NotNull protected final String myExpression;

  /**
   * @param linkText     text of the link what will be appended to a variables tree node text
   * @param debugProcess
   * @param expression
   */
  protected PyFullValueEvaluator(String linkText, @NotNull PyFrameAccessor debugProcess, @NotNull String expression) {
    super(linkText);
    myDebugProcess = debugProcess;
    myExpression = expression;
  }


  protected PyFullValueEvaluator(@NotNull PyFrameAccessor debugProcess, @NotNull String expression) {
    myDebugProcess = debugProcess;
    myExpression = expression;
  }


  @Override
  public void startEvaluation(@NotNull XFullValueEvaluationCallback callback) {
    String expression = myExpression.trim();
    if (expression.isEmpty()) {
      callback.evaluated("");
      return;
    }

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        final PyDebugValue value = myDebugProcess.evaluate(expression, false, false);
        if (value.getValue() == null) {
          throw new PyDebuggerException("Failed to Load Value");
        }
        callback.evaluated(value.getValue());
        ApplicationManager.getApplication().invokeLater(() -> showCustomPopup(myDebugProcess, value));
      }
      catch (PyDebuggerException e) {
        callback.errorOccurred(e.getTracebackError());
      }
    });
  }

  protected void showCustomPopup(PyFrameAccessor debugProcess, PyDebugValue debugValue) {

  }
}
