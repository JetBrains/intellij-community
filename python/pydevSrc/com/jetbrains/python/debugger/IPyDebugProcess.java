package com.jetbrains.python.debugger;

import java.util.List;

/**
 * @author yole
 */
public interface IPyDebugProcess {
  PyPositionConverter getPositionConverter();

  void threadSuspended(PyThreadInfo thread);

  List<PyDebugValue> loadVariable(PyDebugValue pyDebugValue) throws PyDebuggerException;

  void changeVariable(PyDebugValue variable, String expression) throws PyDebuggerException;

  boolean isVariable(String name);

  void threadResumed(PyThreadInfo thread);

  PyDebugValue evaluate(String expression, boolean exec, boolean doTrunc) throws PyDebuggerException;
}
