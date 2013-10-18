package com.jetbrains.python.debugger;

import com.intellij.openapi.application.ApplicationManager;
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
  public void setValue(@NotNull final String expression, @NotNull final XModificationCallback callback) {
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        try {
          myDebugProcess.changeVariable(myVariable, expression);
          callback.valueModified();
        }
        catch (PyDebuggerException e) {
          callback.errorOccurred(e.getTracebackError());
        }
      }
    });
  }

  @Override
  public String getInitialValueEditorText() {
    return PyTypeHandler.format(myVariable);
  }

}
