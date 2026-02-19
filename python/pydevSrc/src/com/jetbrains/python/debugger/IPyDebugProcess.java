// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.jetbrains.python.debugger.pydev.PyDebugCallback;

import java.io.IOException;


public interface IPyDebugProcess extends PyFrameAccessor {
  PyPositionConverter getPositionConverter();

  void threadSuspended(PyThreadInfo thread, boolean updateSourcePosition);

  boolean canSaveToTemp(String name);

  void threadResumed(PyThreadInfo thread);

  void printToConsole(String text, ConsoleViewContentType contentType);

  void init();

  int handleDebugPort(int port) throws IOException;

  void recordSignature(PySignature signature);

  void recordLogEvent(PyConcurrencyEvent event);

  void showConsole(PyThreadInfo thread);

  void loadReferrers(PyReferringObjectsValue var, PyDebugCallback<? super XValueChildrenList> callback);

  void suspendAllOtherThreads(PyThreadInfo thread);

  boolean isSuspendedOnAllThreadsPolicy();

  void consoleInputRequested(boolean isStarted);

  void showWarning(String warningId);

  XDebugSession getSession();
}
