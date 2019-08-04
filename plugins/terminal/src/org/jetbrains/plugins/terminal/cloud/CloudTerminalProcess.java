// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.cloud;

import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Consumer;

public class CloudTerminalProcess extends Process {

  private final OutputStream myOutputStream;
  private final InputStream myInputStream;
  private final Consumer<Dimension> myTtyResizeHandler;
  private final Semaphore mySemaphore;

  public CloudTerminalProcess(OutputStream terminalInput, InputStream terminalOutput, @Nullable Consumer<Dimension> ttyResizeHandler) {
    myOutputStream = terminalInput;
    myInputStream = terminalOutput;
    myTtyResizeHandler = ttyResizeHandler;
    mySemaphore = new Semaphore();
    mySemaphore.down();
  }

  public CloudTerminalProcess(OutputStream terminalInput, InputStream terminalOutput) {
    this(terminalInput, terminalOutput, null);
  }

  @Override
  public OutputStream getOutputStream() {
    return myOutputStream;
  }

  @Override
  public InputStream getInputStream() {
    return myInputStream;
  }

  @Override
  public InputStream getErrorStream() {
    return null;
  }

  @Override
  public int waitFor() throws InterruptedException {
    mySemaphore.waitFor();
    return exitValue();
  }

  @Override
  public int exitValue() {
    return 0;
  }

  @Override
  public void destroy() {
    mySemaphore.up();
  }

  void resizeTty(@NotNull Dimension termSize) {
    if (myTtyResizeHandler != null) {
      myTtyResizeHandler.accept(termSize);
    }
  }
}
