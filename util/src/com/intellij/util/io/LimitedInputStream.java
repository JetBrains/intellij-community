/*
 * @author max
 */
package com.intellij.util.io;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class LimitedInputStream extends FilterInputStream {
  private int myReadLimit;
  private int myBytesRead;

  public LimitedInputStream(final InputStream in, final int readLimit) {
    super(in);
    myReadLimit = readLimit;
    myBytesRead = 0;
  }

  public boolean markSupported() {
    return false;
  }

  public int read() throws IOException {
    if (myBytesRead == myReadLimit) return -1;
    final int r = super.read();
    if (r >= 0) myBytesRead++;
    return r;
  }

  public int read(byte[] b) throws IOException {
    return read(b, 0, b.length);
  }

  public int read(byte[] b, int off, int len) throws IOException {
    if (myBytesRead >= myReadLimit) return -1;
    len = Math.min(len, myReadLimit - myBytesRead);
    if (len <= 0) return -1;

    final int acutallyRead = super.read(b, off, len);
    if (acutallyRead >= 0) myBytesRead += acutallyRead;

    return acutallyRead;
  }

  public long skip(long n) throws IOException {
    n = Math.min(n, myReadLimit - myBytesRead);
    if (n <= 0) return 0;

    final long skipped = super.skip(n);
    myBytesRead += skipped;
    return skipped;
  }

  public int available() throws IOException {
    return Math.min(super.available(), myReadLimit - myBytesRead);
  }

  public synchronized void mark(final int readlimit) {
    throw new UnsupportedOperationException();
  }

  public synchronized void reset() throws IOException {
    throw new UnsupportedOperationException();
  }
}