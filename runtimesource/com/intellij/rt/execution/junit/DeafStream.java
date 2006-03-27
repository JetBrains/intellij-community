package com.intellij.rt.execution.junit;

import java.io.OutputStream;
import java.io.IOException;
import java.io.PrintStream;

public class DeafStream extends OutputStream {
  public static final DeafStream CURRENT = new DeafStream();
  public static final PrintStream DEAF_PRINT_STREAM = new PrintStream(CURRENT);

  public void write(int b) throws IOException {
  }
}
