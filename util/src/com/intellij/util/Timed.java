package com.intellij.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author mike
 */
@SuppressWarnings({"NonPrivateFieldAccessedInSynchronizedContext"})
abstract class Timed<T> implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.Timed");
  private static final Map<Timed, Boolean> ourReferences = Collections.synchronizedMap(new WeakHashMap<Timed, Boolean>());

  int myLastCheckedAccessCount;
  int myAccessCount;
  protected T myT;
  boolean myPolled;

  protected Timed(final Disposable parentDisposable) {
    if (parentDisposable != null) {
      Disposer.register(parentDisposable, this);
    }
  }

  public synchronized void dispose() {
    final Object t = myT;
    myT = null;
    if (t instanceof Disposable) {
      Disposable disposable = (Disposable)t;
      Disposer.dispose(disposable);
    }

    remove();
  }

  protected final void poll() {
    if (!myPolled) {
      ourReferences.put(this, Boolean.TRUE);
      myPolled = true;
    }
  }

  protected final void remove() {
    ourReferences.remove(this);
    myPolled = false;
  }

  protected synchronized boolean isLocked() {
    return false;
  }


  static {
    ScheduledExecutorService service = ConcurrencyUtil.newSingleScheduledThreadExecutor("timed reference disposer");
    service.scheduleWithFixedDelay(new Runnable() {
      public void run() {
        try {
          final Timed[] references = ourReferences.keySet().toArray(new Timed[ourReferences.size()]);
          for (Timed timed : references) {
            if (timed == null) continue;
            synchronized (timed) {
              if (timed.myLastCheckedAccessCount == timed.myAccessCount && !timed.isLocked()) {
                timed.dispose();
              }
              else {
                timed.myLastCheckedAccessCount = timed.myAccessCount;
              }
            }
          }
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
    }, 60, 60, TimeUnit.SECONDS);
  }
}
