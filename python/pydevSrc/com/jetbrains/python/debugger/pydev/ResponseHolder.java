package com.jetbrains.python.debugger.pydev;

import com.jetbrains.python.debugger.pydev.transport.DebuggerTransport;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class ResponseHolder {
  private static final int RESPONSE_TIMEOUT = 60000;
  private int mySequence = -1;
  private final Object mySequenceObject = new Object(); // for synchronization on mySequence
  private final Map<Integer, ProtocolFrame> myResponseQueue = new HashMap<>();
  private final DebuggerTransport myTransport;

  public ResponseHolder(DebuggerTransport transport) {
    myTransport = transport;
  }

  public int getNextSequence() {
    synchronized (mySequenceObject) {
      mySequence += 2;
      return mySequence;
    }
  }

  public void placeResponse(final int sequence, final ProtocolFrame response) {
    synchronized (myResponseQueue) {
      if (response == null || myResponseQueue.containsKey(sequence)) {
        myResponseQueue.put(sequence, response);
      }
      if (response != null) {
        myResponseQueue.notifyAll();
      }
    }
  }

  @Nullable
  public ProtocolFrame waitForResponse(final int sequence) {
    ProtocolFrame response;
    long until = System.currentTimeMillis() + RESPONSE_TIMEOUT;

    synchronized (myResponseQueue) {
      do {
        try {
          myResponseQueue.wait(1000);
        }
        catch (InterruptedException ignore) {
        }
        response = myResponseQueue.get(sequence);
      }
      while (response == null && myTransport.isConnected() && System.currentTimeMillis() < until);
      myResponseQueue.remove(sequence);
    }

    return response;
  }

  public void cleanUp() {
    myResponseQueue.clear();
    synchronized (mySequenceObject) {
      mySequence = -1;
    }
  }

}
