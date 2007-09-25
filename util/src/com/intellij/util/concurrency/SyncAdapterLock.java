/*
 * @author max
 */
package com.intellij.util.concurrency;

public class SyncAdapterLock implements JBLock {
  private final Sync myAdaptee;

  public SyncAdapterLock(final Sync adaptee) {
    myAdaptee = adaptee;
  }

  public void lock() {
    try {
      myAdaptee.acquire();
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  public void unlock() {
    myAdaptee.release();
  }
}