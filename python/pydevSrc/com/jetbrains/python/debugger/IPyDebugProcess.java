package com.jetbrains.python.debugger;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.xdebugger.frame.XValueChildrenList;

/**
 * @author yole
 */
public interface IPyDebugProcess {
  PyPositionConverter getPositionConverter();

  void threadSuspended(PyThreadInfo thread);

  XValueChildrenList loadVariable(PyDebugValue var) throws PyDebuggerException;

  void changeVariable(PyDebugValue variable, String expression) throws PyDebuggerException;

  boolean isVariable(String name);

  void threadResumed(PyThreadInfo thread);

  PyDebugValue evaluate(String expression, boolean exec, boolean doTrunc) throws PyDebuggerException;

  void printToConsole(String text, ConsoleViewContentType contentType);
}
