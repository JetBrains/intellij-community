// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.xdebugger.frame.XFullValueEvaluator;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class PyFullValueEvaluator extends XFullValueEvaluator {
  protected final @NotNull PyFrameAccessor myDebugProcess;
  protected final @NotNull String myExpression;

  /**
   * @param linkText     text of the link what will be appended to a variables tree node text
   */
  protected PyFullValueEvaluator(@Nls String linkText, @NotNull PyFrameAccessor debugProcess, @NotNull String expression) {
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
    doEvaluate(callback, false);
  }

  protected boolean isCopyValueCallback(XFullValueEvaluationCallback callback) {
    return callback instanceof PyCopyValueEvaluationCallback;
  }

  protected void doEvaluate(@NotNull XFullValueEvaluationCallback callback, boolean doTrunc) {
    String expression = myExpression.trim();
    if (expression.isEmpty()) {
      callback.evaluated("");
      return;
    }

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        final PyDebugValue value = myDebugProcess.evaluate(expression, false, doTrunc);
        if (value.getValue() == null) {
          throw new PyDebuggerException("Failed to Load Value");
        }
        callback.evaluated(value.getValue());
        if (!isCopyValueCallback(callback)) {
          ApplicationManager.getApplication().invokeLater(() -> showCustomPopup(myDebugProcess, value));
        }
      }
      catch (PyDebuggerException e) {
        callback.errorOccurred(e.getTracebackError());
      }
    });
  }

  protected void showCustomPopup(PyFrameAccessor debugProcess, PyDebugValue debugValue) {

  }
}
