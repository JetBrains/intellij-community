/*
 * @author max
 */
package com.intellij.util.concurrency;

import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DefaultReentrantReadWriteLockAdapter implements JBReentrantReadWriteLock {
  private final DefaultLockAdapter myReadLock;
  private final DefaultLockAdapter myWriteLock;
  private final ReentrantReadWriteLock myAdaptee;

  public DefaultReentrantReadWriteLockAdapter() {
    myAdaptee = new ReentrantReadWriteLock();
    myReadLock = new DefaultLockAdapter(myAdaptee.readLock());
    myWriteLock = new DefaultLockAdapter(myAdaptee.writeLock());
  }

  public JBLock readLock() {
    return myReadLock;
  }

  public JBLock writeLock() {
    return myWriteLock;
  }

  public boolean isWriteLockedByCurrentThread() {
    return myAdaptee.isWriteLockedByCurrentThread();
  }
}