package com.jetbrains.python.debugger;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.xdebugger.frame.XValueChildrenList;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * @author yole
 */
public interface IPyDebugProcess extends PyEvaluator {
  PyPositionConverter getPositionConverter();

  void threadSuspended(PyThreadInfo thread);

  XValueChildrenList loadVariable(PyDebugValue var) throws PyDebuggerException;

  void changeVariable(PyDebugValue variable, String expression) throws PyDebuggerException;

  boolean isVariable(String name);

  void threadResumed(PyThreadInfo thread);

  void printToConsole(String text, ConsoleViewContentType contentType);

  void init();

  int handleDebugPort(int port) throws IOException;

  void recordSignature(PySignature signature);
}
