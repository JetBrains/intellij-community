package com.jetbrains.python.debugger;

import org.jetbrains.annotations.NotNull;

public class PyLoadingValueEvaluator extends PyFullValueEvaluator {
  protected PyLoadingValueEvaluator(@NotNull String linkText, @NotNull PyFrameAccessor debugProcess, @NotNull String expression) {
    super(linkText, debugProcess, expression);
  }

  @Override
  public void startEvaluation(@NotNull XFullValueEvaluationCallback callback) {
    callback.evaluated("... Loading value");
  }

  @Override
  public boolean isShowValuePopup() {
    return false;
  }
}
