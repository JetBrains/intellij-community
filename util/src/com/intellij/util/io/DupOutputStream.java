/*
 * @author max
 */
package com.intellij.util.io;

import java.io.IOException;
import java.io.OutputStream;

public class DupOutputStream extends OutputStream {
  private final OutputStream myStream1;
  private final OutputStream myStream2;

  public DupOutputStream(final OutputStream stream1, final OutputStream stream2) {
    myStream1 = stream1;
    myStream2 = stream2;
  }

  public void write(final int b) throws IOException {
    myStream1.write(b);
    myStream2.write(b);
  }

  public void close() throws IOException {
    myStream1.close();
    myStream2.close();
  }

  public void flush() throws IOException {
    myStream1.flush();
    myStream2.flush();
  }

  public void write(final byte[] b) throws IOException {
    myStream1.write(b);
    myStream2.write(b);
  }

  public void write(final byte[] b, final int off, final int len) throws IOException {
    myStream1.write(b, off, len);
    myStream2.write(b, off, len);
  }
}