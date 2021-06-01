// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.frame.XValueModifier;
import org.jetbrains.annotations.NotNull;


public class PyValueModifier extends XValueModifier {

  private final PyFrameAccessor myDebugProcess;
  private final PyDebugValue myVariable;

  public PyValueModifier(final PyFrameAccessor debugProcess, final PyDebugValue variable) {
    myDebugProcess = debugProcess;
    myVariable = variable;
  }

  @Override
  public void setValue(@NotNull final XExpression expression, @NotNull final XModificationCallback callback) {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        myDebugProcess.changeVariable(myVariable, expression.getExpression());
        callback.valueModified();
      }
      catch (PyDebuggerException e) {
        callback.errorOccurred(e.getTracebackError());
      }
    });
  }

  @Override
  public String getInitialValueEditorText() {
    return PyTypeHandler.format(myVariable);
  }

}
