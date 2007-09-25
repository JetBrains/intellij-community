/*
 * @author max
 */
package com.intellij.util.concurrency;

public class SynchronizedBasedReentrantReadWriteLock implements JBReentrantReadWriteLock {
  private final SyncAdapterLock myReadLock;
  private final SyncAdapterLock myWriteLock;
  private final ReentrantWriterPreferenceReadWriteLock myAdaptee;

  public SynchronizedBasedReentrantReadWriteLock() {
    myAdaptee = new ReentrantWriterPreferenceReadWriteLock();
    myReadLock = new SyncAdapterLock(myAdaptee.readLock());
    myWriteLock = new SyncAdapterLock(myAdaptee.writeLock());
  }

  public JBLock readLock() {
    return myReadLock;
  }

  public JBLock writeLock() {
    return myWriteLock;
  }

  public boolean isWriteLockedByCurrentThread() {
    return myAdaptee.isWriteLockAcquired(Thread.currentThread());
  }
}