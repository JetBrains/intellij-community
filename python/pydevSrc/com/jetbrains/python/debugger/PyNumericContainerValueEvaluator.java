// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger;

import com.jetbrains.python.debugger.pydev.tables.PyNumericContainerPopupCustomizer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PyNumericContainerValueEvaluator extends PyFullValueEvaluator {

  protected PyNumericContainerValueEvaluator(@Nls String linkText, PyFrameAccessor debugProcess, String expression) {
    super(linkText, debugProcess, expression);
  }

  @Override
  protected void showCustomPopup(PyFrameAccessor debugProcess, PyDebugValue debugValue) {
    List<PyNumericContainerPopupCustomizer> providers = PyNumericContainerPopupCustomizer.EP_NAME.getExtensionList();
    for (PyNumericContainerPopupCustomizer provider : providers) {
      if (provider.showFullValuePopup(debugProcess, debugValue)) {
        break;
      }
    }
  }

  @Override
  public void startEvaluation(@NotNull XFullValueEvaluationCallback callback) {
    doEvaluate(callback, !isCopyValueCallback(callback));
  }

  @Override
  public boolean isShowValuePopup() {
    return false;
  }
}
