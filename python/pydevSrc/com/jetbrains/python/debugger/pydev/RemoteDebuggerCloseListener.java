package com.jetbrains.python.debugger.pydev;

/**
 * @author traff
 */
public interface RemoteDebuggerCloseListener {
  void closed();

  void communicationError();

  void exitEvent();
}
