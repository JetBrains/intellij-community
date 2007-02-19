package com.intellij.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public abstract class TimedReference<T> implements Disposable {
  private static final List<TimedReference> ourReferences = Collections.synchronizedList(new ArrayList<TimedReference>());
  private T t;
  private int myAcquireCount = 0;
  private int myAccessCount = 0;
  private int myLastCheckedAccessCount = 0;

  public TimedReference(Disposable parentDisposable) {
    ourReferences.add(this);
    if (parentDisposable != null) {
      Disposer.register(parentDisposable, this);
    }
  }

  public synchronized T acquire() {
    myAccessCount++;
    myAcquireCount++;

    if (t == null) t = calc();
    ourReferences.add(this);
    return t;
  }

  public synchronized void release() {
    myAcquireCount--;

    assert myAcquireCount >= 0;
  }

  public synchronized void dispose() {
    assert myAcquireCount == 0;
    ourReferences.remove(this);
  }

  @NotNull
  protected abstract T calc();

  static {
    ScheduledExecutorService service = ConcurrencyUtil.newSingleScheduledThreadExecutor("timed reference disposer");
    service.scheduleWithFixedDelay(new Runnable() {
      public void run() {
        final TimedReference[] references = ourReferences.toArray(new TimedReference[ourReferences.size()]);
        for (TimedReference reference : references) {
          synchronized (reference) {
            if (reference.myLastCheckedAccessCount == reference.myAccessCount && reference.myAcquireCount == 0) {
              final Object t = reference.t;
              reference.t = null;
              if (t instanceof Disposable) {
                Disposable disposable = (Disposable)t;
                Disposer.dispose(disposable);
              }
              ourReferences.remove(reference);
            }
            else {
              reference.myLastCheckedAccessCount = reference.myAccessCount;
            }
          }
        }
      }
    }, 60, 60, TimeUnit.SECONDS);
  }

}
