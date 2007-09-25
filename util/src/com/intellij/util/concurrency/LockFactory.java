/*
 * @author max
 */
package com.intellij.util.concurrency;

import com.intellij.Patches;

public class LockFactory {
  private LockFactory() {}

  public static JBReentrantReadWriteLock createReadWriteLock() {
    if (Patches.APPLE_BUG_ID_5359442) {
      return new SynchronizedBasedReentrantReadWriteLock();
    }
    else {
      return new DefaultReentrantReadWriteLockAdapter();
    }
  }
}