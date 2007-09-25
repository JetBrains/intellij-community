/*
 * @author max
 */
package com.intellij.util.concurrency;

public interface JBLock {
  void lock();
  void unlock();
}