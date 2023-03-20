// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server.security;

import java.util.Scanner;
import java.util.concurrent.atomic.AtomicReference;

public class TokenReader {

  public static final String PREFIX = "token=";
  private final AtomicReference<String> myRef = new AtomicReference<String>();
  private final AtomicReference<Throwable> myExceptionRef = new AtomicReference<Throwable>();
  private final Scanner myScanner;

  public TokenReader(Scanner scanner, int timeoutMillis) {
    myScanner = scanner;
    startReading(timeoutMillis);
  }

  public MavenToken getToken() throws Throwable {
    if (myExceptionRef.get() != null) {
      throw myExceptionRef.get();
    }
    if (myRef.get() == null) {
      throw new IllegalStateException("Didn't receive token");
    }
    return new MavenToken(myRef.get());
  }

  private void startReading(final int timeoutMillis) {
    Thread newThread = new MyReadThread();
    newThread.setDaemon(true);
    newThread.start();

    waitUntilReadOrTimeout(timeoutMillis, newThread);
  }

  @SuppressWarnings("BusyWait")
  private void waitUntilReadOrTimeout(int timeoutMillis, Thread newThread) {
    long started = System.currentTimeMillis();
    long deadline = started + timeoutMillis;

    boolean interrupted = false;
    try {
      while (!(myRef.get() != null || myExceptionRef.get() != null) && System.currentTimeMillis() < deadline) {
        Thread.sleep(10);
      }
    }
    catch (InterruptedException e) {
      interrupted = true;
    }
    finally {
      newThread.interrupt();
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private class MyReadThread extends Thread {

    MyReadThread() {
      super("Key Read Thread");
    }

    @Override
    public void run() {
      try {
        while (myScanner.hasNextLine() && !isInterrupted()) {
          String line = myScanner.nextLine();
          if (line != null) {
            line = line.trim();
            if (line.startsWith(PREFIX)) {
              myRef.set(line.substring(PREFIX.length()));
              return;
            }
          }
        }
      }
      catch (Throwable e) {
        myExceptionRef.set(e);
      }
    }
  }
}
