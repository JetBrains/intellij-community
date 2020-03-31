// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.pydev;

import com.intellij.openapi.util.Pair;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.jetbrains.python.console.pydev.PydevCompletionVariant;
import com.jetbrains.python.debugger.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public interface ProcessDebugger {
  String handshake() throws PyDebuggerException;

  PyDebugValue evaluate(String threadId,
                        String frameId,
                        String expression, boolean execute) throws PyDebuggerException;

  PyDebugValue evaluate(String threadId,
                        String frameId,
                        String expression,
                        boolean execute,
                        boolean trimResult)
    throws PyDebuggerException;

  void consoleExec(String threadId, String frameId, String expression, PyDebugCallback<String> callback);

  XValueChildrenList loadFrame(String threadId, String frameId) throws PyDebuggerException;

  List<Pair<String, Boolean>> getSmartStepIntoVariants(String threadId, String frameId, int startContextLine, int endContextLine) throws PyDebuggerException;

  // todo: don't generate temp variables for qualified expressions - just split 'em
  XValueChildrenList loadVariable(String threadId, String frameId, PyDebugValue var) throws PyDebuggerException;

  ArrayChunk loadArrayItems(String threadId,
                            String frameId,
                            PyDebugValue var,
                            int rowOffset,
                            int colOffset,
                            int rows,
                            int cols,
                            String format) throws PyDebuggerException;

  void loadReferrers(String threadId, String frameId, PyReferringObjectsValue var, PyDebugCallback<XValueChildrenList> callback);

  PyDebugValue changeVariable(String threadId, String frameId, PyDebugValue var, String value)
    throws PyDebuggerException;

  void loadFullVariableValues(@NotNull String threadId,
                              @NotNull String frameId,
                              @NotNull List<PyFrameAccessor.PyAsyncValue<String>> vars)
    throws PyDebuggerException;

  @Nullable
  String loadSource(String path);

  Collection<PyThreadInfo> getThreads();

  void execute(@NotNull AbstractCommand command);

  void suspendAllThreads();

  void suspendThread(String threadId);

  /**
   * Disconnects current debug process. Closes all resources.
   */
  void close();

  boolean isConnected();

  void waitForConnect() throws Exception;

  /**
   * Disconnects currently connected process. After that it can wait for the next.
   */
  void disconnect();

  void run() throws PyDebuggerException;

  void smartStepInto(String threadId, String frameId, String functionName, int callOrder, int contextStartLine, int contextEndLine);

  void resumeOrStep(String threadId, ResumeOrStepCommand.Mode mode);

  void setNextStatement(@NotNull String threadId,
                        @NotNull XSourcePosition sourcePosition,
                        @Nullable String functionName,
                        @NotNull PyDebugCallback<Pair<Boolean, String>> callback);

  void setTempBreakpoint(@NotNull String type, @NotNull String file, int line);

  void removeTempBreakpoint(@NotNull String file, int line);

  void setBreakpoint(@NotNull String typeId, @NotNull String file, int line, @Nullable String condition, @Nullable String logExpression,
                     @Nullable String funcName, @NotNull SuspendPolicy policy);

  void removeBreakpoint(@NotNull String typeId, @NotNull String file, int line);

  void setShowReturnValues(boolean isShowReturnValues);

  void addCloseListener(RemoteDebuggerCloseListener remoteDebuggerCloseListener);

  List<PydevCompletionVariant> getCompletions(String threadId, String frameId, String prefix);

  String getDescription(String threadId, String frameId, String cmd);


  void addExceptionBreakpoint(ExceptionBreakpointCommandFactory factory);

  void removeExceptionBreakpoint(ExceptionBreakpointCommandFactory factory);

  void suspendOtherThreads(PyThreadInfo thread);
}
