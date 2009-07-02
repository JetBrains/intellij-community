package com.intellij.lifecycle;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class SlowlyClosingAlarm implements AtomicSectionsAware, Disposable {
  private final static Logger LOG = Logger.getInstance("#com.intellij.lifecycle.SlowlyClosingAlarm");

  // todo think about shared thread
  private final ExecutorService myExecutorService;
  // single threaded executor, so we have "shared" state here 
  private boolean myInUninterruptibleState;
  private boolean myDisposeStarted;
  private boolean myFinished;
  // for own threads only
  private final List<Future<?>> myFutureList;
  private final Object myLock;

  private static final ThreadFactory THREAD_FACTORY_OWN = threadFactory("SlowlyClosingAlarm pool");
  private final String myName;

  private static ThreadFactory threadFactory(@NonNls final String threadsName) {
    return new ThreadFactory() {
      public Thread newThread(final Runnable r) {
        return new Thread(r, threadsName);
      }
    };
  }

  public void dispose() {
    synchronized (myLock) {
      safelyShutdownExecutor();
      for (Future<?> future : myFutureList) {
        future.cancel(true);
      }
      myFutureList.clear();
    }
  }

  public SlowlyClosingAlarm(@NotNull final Project project, @NotNull final String name) {
    myName = name;
    myLock = new Object();
    myFutureList = new ArrayList<Future<?>>();
    myExecutorService = Executors.newSingleThreadExecutor(THREAD_FACTORY_OWN);
    Disposer.register(project, this);
    PeriodicalTasksCloser.getInstance(project).register(name, new Runnable() {
      public void run() {
        waitAndInterrupt(ProgressManager.getInstance().getProgressIndicator());
      }
    });
  }

  private void debug(final String s) {
    LOG.debug(myName + " " + s);
  }

  // todo maybe further allow delayed invocation
  public void addRequest(final Runnable runnable) {
    synchronized (myLock) {
      if (myDisposeStarted) return;
      final MyWrapper wrapper = new MyWrapper(runnable);
      final Future<?> future = myExecutorService.submit(wrapper);
      wrapper.setFuture(future);
      myFutureList.add(future);
      debug("request added");
    }
  }

  public void enter() {
    synchronized (myLock) {
      debug("entering section");
      if (myDisposeStarted) {
        debug("self-interrupting (1)");
        //myLock.notifyAll();
        Thread.currentThread().interrupt();
      }
      myInUninterruptibleState = true;
    }
  }

  public void exit() {
    debug("exiting section");
    synchronized (myLock) {
      myInUninterruptibleState = false;
      if (myDisposeStarted) {
        debug("self-interrupting (2)");
        //myLock.notifyAll();
        Thread.currentThread().interrupt();
      }
    }
  }

  public boolean shouldExitAsap() {
    synchronized (myLock) {
      return myDisposeStarted;
    }
  }

  private void safelyShutdownExecutor() {
    synchronized (myLock) {
      try {
        myExecutorService.shutdown();
      } catch (SecurityException e) {
        //
      }
    }
  }

  public void waitAndInterrupt(@Nullable final ProgressIndicator indicator) {
    final List<Future<?>> copy;
    synchronized (myLock) {
      debug("starting shutdown: " + myFutureList.size());
      myDisposeStarted = true;
      safelyShutdownExecutor();

      copy = new ArrayList<Future<?>>(myFutureList.size());
      for (Future<?> future : myFutureList) {
        if (future.isDone()) continue;
        copy.add(future);
      }
    }

    debug("waiting for gets");
    boolean wasCanceled = false;
    for (Future<?> future : copy) {
      if (wasCanceled) break;
      while (true) {
        try {
          if (indicator == null) {
            future.get();
          } else {
            future.get(500, TimeUnit.MILLISECONDS);
          }
        } catch (CancellationException e) {
          break;
        } catch (InterruptedException e) {
          break;
        }
        catch (ExecutionException e) {
          break;
        }
        catch (TimeoutException e) {
          if (indicator != null) {
            wasCanceled |= indicator.isCanceled();
            if (wasCanceled) {
              break;
            }
            debug("was canceled");
          }
          continue;
        }
        break;
      }
    }

    debug("finishing " + myInUninterruptibleState);
    synchronized (myLock) {
      for (Future<?> future : myFutureList) {
        future.cancel(true);
      }
      myFutureList.clear();
      myFinished = true;
    }
    debug("done");
  }

  /*public void waitAndInterrupt(@Nullable final ProgressIndicator indicator) {
    synchronized (myLock) {
      debug("starting shutdown: " + myFutureList.size());
      myDisposeStarted = true;
      myExecutorService.shutdown();

      while (myInUninterruptibleState) {
        if (indicator != null) {
          if (indicator.isCanceled()) {
            debug("cancelling...");
            for (Future<?> future : myFutureList) {
              future.cancel(true);
            }
            myFutureList.clear();
            return;
          }
        }
        
        try {
          debug("going to wait...");
          myLock.wait(500);
        }
        catch (InterruptedException e) {
          //
        }
      }
      // cancel those not in uninterruptible state
      boolean success = true;
      for (Future<?> future : myFutureList) {
        success &= future.cancel(true);
      }
      myFutureList.clear();

      myFinished = true;
      debug("finished " + success);
    }
  }*/

  public boolean isFinished() {
    synchronized (myLock) {
      return myFinished;
    }
  }

  public boolean isSafeFinished() {
    synchronized (myLock) {
      return myFinished && (! myInUninterruptibleState);
    }
  }

  private class MyWrapper implements Runnable {
    private final Runnable myDelegate;
    private Future myFuture;

    private MyWrapper(final Runnable delegate) {
      myDelegate = delegate;
    }

    public void setFuture(final Future future) {
      myFuture = future;
    }

    public void run() {
      try {
        debug("wrapper starts runnable");
        myDelegate.run();
        debug("wrapper: runnable succesfully finished");
      } finally {
        // anyway, the owner Future is no more interesting for us: its task is finished and does not require "anti-closing" defence
        if (myFuture != null) {
          debug("removing future");
          synchronized (myLock) {
            myFutureList.remove(myFuture);
          }
        }
      }
    }
  }
}
