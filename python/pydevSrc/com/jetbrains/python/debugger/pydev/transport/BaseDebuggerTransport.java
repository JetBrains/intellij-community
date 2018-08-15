package com.jetbrains.python.debugger.pydev.transport;

import com.intellij.openapi.diagnostic.Logger;
import com.jetbrains.python.debugger.pydev.ProtocolFrame;
import com.jetbrains.python.debugger.pydev.RemoteDebugger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.SocketException;
import java.util.Date;

/**
 * @author Alexander Koshevoy
 */
public abstract class BaseDebuggerTransport implements DebuggerTransport {
  private static final Logger LOG = Logger.getInstance(BaseDebuggerTransport.class);

  protected final Object mySocketObject = new Object();

  @NotNull protected RemoteDebugger myDebugger;

  protected BaseDebuggerTransport(@NotNull RemoteDebugger debugger) {myDebugger = debugger;}

  @Override
  public boolean sendFrame(@NotNull final ProtocolFrame frame) {
    logFrame(frame, true);

    try {
      final byte[] packed = frame.pack();
      return sendMessageImpl(packed);
    }
    catch (SocketException se) {
      onSocketException();
    }
    catch (IOException e) {
      LOG.debug(e);
    }
    return false;
  }

  protected abstract boolean sendMessageImpl(byte[] packed) throws IOException;

  protected abstract void onSocketException();

  public static void logFrame(ProtocolFrame frame, boolean out) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(String.format("%1$tH:%1$tM:%1$tS.%1$tL %2$s %3$s\n", new Date(), (out ? "<<<" : ">>>"), frame));
    }
  }
}
