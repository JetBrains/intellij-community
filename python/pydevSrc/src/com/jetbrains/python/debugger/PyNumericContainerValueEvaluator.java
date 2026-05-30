// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger;

import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Disposer;
import com.jetbrains.python.debugger.pydev.tables.PyNumericContainerPopupCustomizer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.util.List;

public class PyNumericContainerValueEvaluator extends PyFullValueEvaluator {

  protected PyNumericContainerValueEvaluator(@Nls String linkText, PyFrameAccessor debugProcess, String expression) {
    super(linkText, debugProcess, expression);
  }

  @Override
  protected void showCustomPopup(PyFrameAccessor debugProcess, PyDebugValue debugValue) {
    Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (focusOwner != null) {
      JRootPane rootPane = SwingUtilities.getRootPane(focusOwner);

      if (rootPane != null) {
        JBPopup popup = (JBPopup)rootPane.getClientProperty(JBPopup.KEY);
        if (popup != null) {
          Disposer.dispose(popup);
        }
      }
    }

    List<PyNumericContainerPopupCustomizer> providers = PyNumericContainerPopupCustomizer.EP_NAME.getExtensionList();
    for (var provider : providers) {
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
