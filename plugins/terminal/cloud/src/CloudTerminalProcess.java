// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.cloud;

import com.intellij.util.concurrency.Semaphore;

import java.io.InputStream;
import java.io.OutputStream;

public class CloudTerminalProcess extends Process {

  private final OutputStream myOutputStream;
  private final InputStream myInputStream;

  private final Semaphore mySemaphore;

  public CloudTerminalProcess(OutputStream terminalInput, InputStream terminalOutput) {
    myOutputStream = terminalInput;
    myInputStream = terminalOutput;
    mySemaphore = new Semaphore();
    mySemaphore.down();
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
}
