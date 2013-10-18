package com.jetbrains.python.debugger.pydev;

/**
 * @author traff
 */
public interface RemoteDebuggerCloseListener {
  /**
   * this event means that process is closed and server is terminated
   */
  void closed();

  /**
   * this event means that we have an error and probably we need to detach debugged process
   */
  void communicationError();

  /**
   * this event means that debug process need to be detached for sure(it is terminated), but server can still accept connections
   * if it is Remote Debug Server
   */
  void detached();
}
