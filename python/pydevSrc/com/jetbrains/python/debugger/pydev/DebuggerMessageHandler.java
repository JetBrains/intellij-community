package com.jetbrains.python.debugger.pydev;

import com.jetbrains.python.debugger.PyDebuggerException;
import org.jetbrains.annotations.NotNull;

public interface DebuggerMessageHandler {
  String handshake() throws PyDebuggerException;

  void disconnect();

  void fireCommunicationError();

  void processResponse(@NotNull final String line);

  void fireExitEvent();
}