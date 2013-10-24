package com.jetbrains.python.debugger.pydev;

import com.intellij.xdebugger.frame.XValueChildrenList;
import com.jetbrains.python.console.pydev.PydevCompletionVariant;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyDebuggerException;
import com.jetbrains.python.debugger.PyThreadInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author traff
 */
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

  void consoleExec(String threadId, String frameId, String expression, DebugCallback<String> callback);

  XValueChildrenList loadFrame(String threadId, String frameId) throws PyDebuggerException;

  // todo: don't generate temp variables for qualified expressions - just split 'em
  XValueChildrenList loadVariable(String threadId, String frameId, PyDebugValue var) throws PyDebuggerException;

  PyDebugValue changeVariable(String threadId, String frameId, PyDebugValue var, String value)
    throws PyDebuggerException;

  @Nullable
  String loadSource(String path);

  Collection<PyThreadInfo> getThreads();

  void execute(@NotNull AbstractCommand command);

  void suspendAllThreads();

  void suspendThread(String threadId);

  /**
   *  Disconnects current debug process. Closes all resources.
   */
  void close();

  boolean isConnected();

  void waitForConnect() throws Exception;

  /**
   * Disconnects currently connected process. After that it can wait for the next.
   */
  void disconnect();

  void run() throws PyDebuggerException;

  void smartStepInto(String threadId, String functionName);

  void resumeOrStep(String threadId, ResumeOrStepCommand.Mode mode);

  void setTempBreakpoint(String type, String file, int line);

  void removeTempBreakpoint(String file, int line);

  void setBreakpoint(String typeId, String file, int line, String condition, String logExpression);

  void removeBreakpoint(String typeId, String file, int line);

  void addCloseListener(RemoteDebuggerCloseListener remoteDebuggerCloseListener);

  List<PydevCompletionVariant> getCompletions(String threadId, String frameId, String prefix);

  void addExceptionBreakpoint(ExceptionBreakpointCommandFactory factory);

  void removeExceptionBreakpoint(ExceptionBreakpointCommandFactory factory);

  /**
  * @author traff
  */
  interface DebugCallback<T> {
    void ok(T value);
    void error(PyDebuggerException exception);
  }
}
