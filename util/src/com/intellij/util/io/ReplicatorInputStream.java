/*
 * @author max
 */
package com.intellij.util.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ReplicatorInputStream extends InputStream {
  private final OutputStream myTarget;
  private final InputStream mySource;

  public ReplicatorInputStream(final InputStream source, final OutputStream target) {
    mySource = source;
    myTarget = target;
  }

  public int read() throws IOException {
    final int b = mySource.read();
    if (b == -1) return -1;
    myTarget.write(b);
    return b;
  }

  public synchronized void mark(final int readlimit) {
    throw new UnsupportedOperationException();
  }

  public boolean markSupported() {
    return false;
  }

  public synchronized void reset() throws IOException {
    throw new UnsupportedOperationException();
  }

  public int read(final byte[] b) throws IOException {
    final int count = mySource.read(b);
    if (count < 0) return count;
    myTarget.write(b, 0, count);
    return count;
  }

  public int read(final byte[] b, final int off, final int len) throws IOException {
    final int count = mySource.read(b, off, len);
    if (count < 0) return count;
    myTarget.write(b, off, count);
    return count;
  }


  public long skip(final long n) throws IOException {
    return read(new byte[(int)n]);
  }

  public int available() throws IOException {
    return mySource.available();
  }

  public void close() throws IOException {
    mySource.close();
    myTarget.close();
  }
}