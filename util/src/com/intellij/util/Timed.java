package com.intellij.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author mike
 */
@SuppressWarnings({"NonPrivateFieldAccessedInSynchronizedContext"})
abstract class Timed<T> implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.Timed");
  private static final Set<Timed> ourReferences = Collections.synchronizedSet(new HashSet<Timed>());

  int myLastCheckedAccessCount;
  int myAccessCount;
  T myT;

  protected Timed(final Disposable parentDisposable) {
    if (parentDisposable != null) {
      Disposer.register(parentDisposable, this);
    }
  }

  public synchronized void dispose() {
    ourReferences.remove(this);
  }

  protected final void poll() {
    ourReferences.add(this);
  }

  protected final void remove() {
    ourReferences.remove(this);
  }

  protected boolean isLocked() {
    return false;
  }


  static {
    ScheduledExecutorService service = ConcurrencyUtil.newSingleScheduledThreadExecutor("timed reference disposer");
    service.scheduleWithFixedDelay(new Runnable() {
      public void run() {
        try {
          final Timed[] references = ourReferences.toArray(new Timed[ourReferences.size()]);
          for (Timed timed : references) {
            synchronized (timed) {
              if (timed.myLastCheckedAccessCount == timed.myAccessCount && !timed.isLocked()) {
                final Object t = timed.myT;
                timed.myT = null;
                if (t instanceof Disposable) {
                  Disposable disposable = (Disposable)t;
                  Disposer.dispose(disposable);
                }
                ourReferences.remove(timed);
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
