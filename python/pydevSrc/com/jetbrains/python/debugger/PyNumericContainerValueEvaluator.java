// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger;

import org.jetbrains.annotations.Nls;

public class PyNumericContainerValueEvaluator extends PyFullValueEvaluator {

  protected PyNumericContainerValueEvaluator(@Nls String linkText, PyFrameAccessor debugProcess, String expression) {
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
