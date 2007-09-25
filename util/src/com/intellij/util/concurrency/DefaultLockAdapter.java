/*
 * @author max
 */
package com.intellij.util.concurrency;

import java.util.concurrent.locks.Lock;

public class DefaultLockAdapter implements JBLock {
  private final Lock myAdaptee;

  public DefaultLockAdapter(final Lock adaptee) {
    myAdaptee = adaptee;
  }

  public void lock() {
    myAdaptee.lock();
  }

  public void unlock() {
    myAdaptee.unlock();
  }
}