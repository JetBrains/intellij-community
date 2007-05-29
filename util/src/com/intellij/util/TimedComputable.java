package com.intellij.util;

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings({"NonPrivateFieldAccessedInSynchronizedContext"})
public abstract class TimedComputable<T>  extends Timed<T> {
  private int myAcquireCount = 0;

  public TimedComputable(Disposable parentDisposable) {
    super(parentDisposable);
  }

  public synchronized T acquire() {
    myAccessCount++;
    myAcquireCount++;

    if (myT == null) myT = calc();
    poll();
    return myT;
  }

  public synchronized void release() {
    myAcquireCount--;

    assert myAcquireCount >= 0;
  }

  public synchronized void dispose() {
    assert myAcquireCount == 0;
    super.dispose();
  }

  protected boolean isLocked() {
    return myAcquireCount != 0;
  }

  @NotNull
  protected abstract T calc();
}
