package com.intellij.rt.execution.junit2.segments;

import java.io.OutputStream;
import java.io.IOException;

public class EchoOutputStream extends OutputStream {
  private OutputStream myOut;
  private OutputStream myEcho;

  public EchoOutputStream(OutputStream out, OutputStream echo) {
    myOut = out;
    myEcho = echo;
  }

  public synchronized void write(int b) throws IOException {
    myOut.write(b);
    myEcho.write(b);
  }
}
