package com.jetbrains.python.debugger;

import com.intellij.execution.ui.ConsoleViewContentType;

import java.io.IOException;

/**
 * @author yole
 */
public interface IPyDebugProcess extends PyFrameAccessor {
  PyPositionConverter getPositionConverter();

  void threadSuspended(PyThreadInfo thread);

  boolean isVariable(String name);

  void threadResumed(PyThreadInfo thread);

  void printToConsole(String text, ConsoleViewContentType contentType);

  void init();

  int handleDebugPort(int port) throws IOException;

  void recordSignature(PySignature signature);
}
