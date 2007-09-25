/*
 * @author max
 */
package com.intellij.util.concurrency;

public interface JBReentrantReadWriteLock {
  JBLock readLock();
  JBLock writeLock();
  boolean isWriteLockedByCurrentThread();
}