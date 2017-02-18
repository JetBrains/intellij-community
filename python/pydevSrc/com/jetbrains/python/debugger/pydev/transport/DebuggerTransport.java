package com.jetbrains.python.debugger.pydev.transport;

import com.jetbrains.python.debugger.pydev.ProtocolFrame;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @author Alexander Koshevoy
 */
public interface DebuggerTransport {
  void waitForConnect() throws IOException;

  void close();

  boolean sendFrame(@NotNull ProtocolFrame frame);

  boolean isConnected();

  void disconnect();
}
