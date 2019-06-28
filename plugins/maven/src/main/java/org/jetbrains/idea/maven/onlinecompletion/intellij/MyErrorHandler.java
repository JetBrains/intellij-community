// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.onlinecompletion.intellij;

import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

import java.util.function.Consumer;

import static org.jetbrains.idea.maven.onlinecompletion.intellij.PackageServiceConfig.BACKOFF_MULTIPLIER;
import static org.jetbrains.idea.maven.onlinecompletion.intellij.PackageServiceConfig.INITIAL_TIMEOUT;
import static org.jetbrains.idea.maven.onlinecompletion.intellij.PackageServiceConfig.MAX_TIMEOUT;

class MyErrorHandler<T> implements Consumer<Throwable> {
  private long myLastFailureTime = -1;
  private Throwable myThrowable;
  private int attemps = 0;

  @Override
  public synchronized void accept(Throwable throwable) {
    if (attemps == 0) {
      myThrowable = throwable;
    }
    attemps++;
    myLastFailureTime = System.currentTimeMillis();
  }

  //todo we need to reset status on network configuration changes
  private boolean isDown() {
    if (myLastFailureTime == -1) {
      return false;
    }
    double value = Math.pow(BACKOFF_MULTIPLIER, attemps) * INITIAL_TIMEOUT + myLastFailureTime;
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      value = myLastFailureTime + MAX_TIMEOUT;
    }
    long timeToCheck = (long)value;
    boolean stillDown = timeToCheck >= System.currentTimeMillis();
    if (!stillDown) {
      markSuccess();
      return false;
    }
    return true;
  }

  public synchronized Promise<Void> errorResult() {
    if (!isDown()) {
      return null;
    }
    AsyncPromise<Void> error = new AsyncPromise<>();
    error.onError(r -> {
    });//to avoid logging
    error.setError(myThrowable);
    return error;
  }

  public synchronized void markSuccess() {
    myLastFailureTime = -1;
    attemps = 0;
  }
}
