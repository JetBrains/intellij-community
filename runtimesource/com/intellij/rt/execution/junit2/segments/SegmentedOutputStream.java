package com.intellij.rt.execution.junit2.segments;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

public class SegmentedOutputStream extends OutputStream implements SegmentedStream, PacketProcessor {
  private final PrintStreamProvider myPrintStreamProvider;
  private boolean myStarted = false;

  public SegmentedOutputStream(PrintStreamProvider provider) {
    myPrintStreamProvider = provider;
    try {
      flush();
    } catch (IOException e) {
      throw new RuntimeException(e.getLocalizedMessage());
    }
  }

  public SegmentedOutputStream(PrintStream transportStream) {
    this (new SimplePrintStreamProvider(transportStream));
  }

  public synchronized void write(int b) throws IOException {
    if (b == SPECIAL_SYMBOL && myStarted)
      writeNext(b);
    writeNext(b);
    flush();
  }

  public synchronized void write(byte b[], int off, int len) throws IOException {
    super.write(b, off, len);
  }

  public synchronized void flush() throws IOException {
    getTransportStream().flush();
  }

  public synchronized void close() throws IOException {
    getTransportStream().close();
  }

  private void writeNext(int b) {
    try {
      getTransportStream().write(b);
    } catch (IOException e) {
      throw new RuntimeException(e.getMessage());
    }
  }

  public synchronized void processPacket(String packet) {
    if (!myStarted)
      sendStart();
    writeNext(MARKER_PREFIX);
    String encodedPacket = Packet.encode(packet);
    writeNext(String.valueOf(encodedPacket.length())+LENGTH_DELIMITER+encodedPacket);
  }

  private void writeNext(String string) {
    try {
      getTransportStream().write(string.getBytes());
    } catch (IOException e) {
      throw new RuntimeException(e.getMessage());
    }
  }

  public void sendStart() {
    writeNext(STARTUP_MESSAGE);
    myStarted = true;
  }

  public void beNotStarted() {
    myStarted = false;
  }

  private OutputStream getTransportStream() {
    return myPrintStreamProvider.getOutputStream();
  }

  public static interface PrintStreamProvider {
    OutputStream getOutputStream();
  }

  public static class SimplePrintStreamProvider implements PrintStreamProvider {
    private final PrintStream myPrintStream;

    public SimplePrintStreamProvider(PrintStream printStream) {
      myPrintStream = printStream;
    }

    public OutputStream getOutputStream() {
      return myPrintStream;
    }
  }
}
