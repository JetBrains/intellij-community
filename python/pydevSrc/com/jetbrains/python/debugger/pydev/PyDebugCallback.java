package com.jetbrains.python.debugger.pydev;

import com.jetbrains.python.debugger.PyDebuggerException;

/**
 * @author traff
 */
public interface PyDebugCallback<T> {
  void ok(T value);

  void error(PyDebuggerException exception);
}
