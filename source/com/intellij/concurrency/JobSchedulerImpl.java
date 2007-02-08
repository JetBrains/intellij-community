/*
 * @author max
 */
package com.intellij.concurrency;

import com.intellij.openapi.application.impl.ApplicationImpl;
import org.jetbrains.annotations.NonNls;

import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@NonNls
public class JobSchedulerImpl extends JobScheduler {
  public final static int CORES_COUNT = Runtime.getRuntime().availableProcessors();
  @NonNls private static final String THREADS_NAME = "JobScheduler pool";
  private final static ThreadFactory WORKERS_FACTORY = new ThreadFactory() {
    public Thread newThread(final Runnable r) {
      return new Thread(r, THREADS_NAME);
    }
  };

  private final static Lock ourSuspensionLock = new ReentrantLock();

  private static final PriorityBlockingQueue<Runnable> ourQueue = new PriorityBlockingQueue<Runnable>() {
    public Runnable poll() {
      final Runnable result = super.poll();

      ourSuspensionLock.lock();
      try {
        return result;
      }
      finally {
        ourSuspensionLock.unlock();
      }
    }

    public Runnable poll(final long timeout, final TimeUnit unit) throws InterruptedException {
      final Runnable result = super.poll(timeout, unit);

      ourSuspensionLock.lock();
      try {
        return result;
      }
      finally {
        ourSuspensionLock.unlock();
      }
    }
  };
  private static final ThreadPoolExecutor ourExecutor = new ThreadPoolExecutor(CORES_COUNT, Integer.MAX_VALUE, 60 * 10, TimeUnit.SECONDS,
                                                                               ourQueue, WORKERS_FACTORY) {
    protected void beforeExecute(final Thread t, final Runnable r) {
      PrioritizedFutureTask task = (PrioritizedFutureTask)r;
      if (task.isParentThreadHasReadAccess()) {
        ApplicationImpl.setExceptionalThreadWithReadAccessFlag(true);
      }
      task.signalStarted();

      // TODO: hook up JobMonitor into thread locals
      super.beforeExecute(t, r);
    }

    protected void afterExecute(final Runnable r, final Throwable t) {
      super.afterExecute(r, t);
      ApplicationImpl.setExceptionalThreadWithReadAccessFlag(false);
      PrioritizedFutureTask task = (PrioritizedFutureTask)r;
      task.signalDone();
      // TODO: cleanup JobMonitor
    }
  };

  private static long ourJobsCounter = 0;

  private static final ScheduledExecutorService ourScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
    public Thread newThread(final Runnable r) {
      return new Thread(r, "Periodic tasks thread");
    }
  });


  public static void execute(PrioritizedFutureTask task) {
    ourExecutor.execute(task);
  }

  public static int currentTaskIndex() {
    final PrioritizedFutureTask topTask = (PrioritizedFutureTask)ourQueue.peek();
    return topTask == null ? 0 : topTask.getTaskIndex();
  }

  public static long currentJobIndex() {
    return ourJobsCounter++;
  }

  public static void suspend() {
    ourSuspensionLock.lock();
  }

  public static void resume() {
    ourSuspensionLock.unlock();
  }

  public <T> Job<T> createJob(String titile, int priority) {
    return new JobImpl<T>(titile, priority);
  }

  public ScheduledFuture<?> schedule(final Runnable command, final long delay, final TimeUnit unit) {
    return ourScheduledExecutorService.schedule(command, delay, unit);
  }

  public ScheduledFuture<?> scheduleAtFixedRate(final Runnable command, final long initialDelay, final long period, final TimeUnit unit) {
    return ourScheduledExecutorService.scheduleAtFixedRate(command, initialDelay, period, unit);
  }

  public ScheduledFuture<?> scheduleWithFixedDelay(final Runnable command, final long initialDelay, final long delay, final TimeUnit unit) {
    return ourScheduledExecutorService.scheduleWithFixedDelay(command, initialDelay, delay, unit);
  }
}