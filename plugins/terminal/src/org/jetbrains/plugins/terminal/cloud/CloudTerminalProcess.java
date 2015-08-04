/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
