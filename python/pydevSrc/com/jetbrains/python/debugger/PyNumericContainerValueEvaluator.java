package com.jetbrains.python.debugger;

public class PyNumericContainerValueEvaluator extends PyFullValueEvaluator {

  protected PyNumericContainerValueEvaluator(String linkText, PyFrameAccessor debugProcess, String expression) {
    super(linkText, debugProcess, expression);
  }

  @Override
  protected void showCustomPopup(PyFrameAccessor debugProcess, PyDebugValue debugValue) {
    debugProcess.showNumericContainer(debugValue);
  }

  @Override
  public boolean isShowValuePopup() {
    return false;
  }
}
