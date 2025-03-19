// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  public void setValue(final @NotNull XExpression expression, final @NotNull XModificationCallback callback) {
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
