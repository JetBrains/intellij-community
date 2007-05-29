package com.intellij.util;

import com.intellij.openapi.Disposable;

@SuppressWarnings({"NonPrivateFieldAccessedInSynchronizedContext"})
public class TimedReference<T> extends Timed<T> {
  public TimedReference(Disposable parentDisposable) {
    super(parentDisposable);
  }

  public synchronized T get() {
    myAccessCount++;
    poll();
    return myT;
  }

  public synchronized void set(T t) {
    myAccessCount++;
    poll();
    myT = t;
  }
}
