package com.intellij.util;

import java.io.OutputStream;
import java.io.IOException;

public class ScrambledOutputStream extends OutputStream{
  static final int MASK = 0xAA;
  private OutputStream myOriginalStream;

  public ScrambledOutputStream(OutputStream originalStream) {
    myOriginalStream = originalStream;
  }

  public void write(int b) throws IOException {
    myOriginalStream.write(b ^ MASK);
  }

  public void write(byte[] b, int off, int len) throws IOException {
    byte[] newBytes = new byte[len];
    for(int i = 0; i < len; i++) {
      newBytes[i] = (byte)(b[off + i] ^ MASK);      
    }
    myOriginalStream.write(newBytes, 0, len);
  }

  public void flush() throws IOException {
    myOriginalStream.flush();
  }

  public void close() throws IOException {
    myOriginalStream.close();
  }

}