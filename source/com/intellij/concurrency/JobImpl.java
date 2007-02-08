/*
 * @author max
 */
package com.intellij.concurrency;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class JobImpl<T> implements Job<T> {
  private final String myTitle;
  private final List<Callable<T>> myTasks = new ArrayList<Callable<T>>();
  private final long myJobIndex = JobSchedulerImpl.currentJobIndex();
  private final int myPriority;
  private List<PrioritizedFutureTask<T>> myFutures;
  private volatile boolean myCanceled = false;

  private final Lock myLock = new ReentrantLock();

  public JobImpl(String title, int priority) {
    myTitle = title;
    myPriority = priority;
  }

  public JobImpl(final String title) {
    this(title, DEFAULT_PRIORITY);
  }

  public String getTitle() {
    return myTitle;
  }

  public void addTask(Callable<T> task) {
    myLock.lock();
    try {
      if (myFutures != null) {
        throw new IllegalStateException("Already running. You can't add tasks to a job, which is already scheduled");
      }

      myTasks.add(task);
    }
    finally {
      myLock.unlock();
    }
  }

  public void addTask(Runnable task, T result) {
    addTask(Executors.callable(task, result));
  }

  public void addTask(Runnable task) {
    addTask(Executors.callable(task, (T)null));
  }

  public List<T> scheduleAndWaitForResults() throws Throwable {
    // Don't bother scheduling if we only have one processor.
    if (JobSchedulerImpl.CORES_COUNT < 2) {
      List<T> results = new ArrayList<T>(myTasks.size());
      for (Callable<T> task : myTasks) {
        myLock.lock();
        try {
          if (myCanceled) return Collections.emptyList();
        }
        finally {
          myLock.unlock();
        }
        results.add(task.call());
      }

      return results;
    }

    myLock.lock();
    try {
      final Application application = ApplicationManager.getApplication();
      boolean callerHasReadAccess = application != null && application.isReadAccessAllowed();

      myFutures = new ArrayList<PrioritizedFutureTask<T>>(myTasks.size());

      int startTaskIndex = JobSchedulerImpl.currentTaskIndex();
      for (final Callable<T> task : myTasks) {
        final PrioritizedFutureTask<T> future = new PrioritizedFutureTask<T>(task, myJobIndex, startTaskIndex++, myPriority, callerHasReadAccess);
        myFutures.add(future);
      }

      for (PrioritizedFutureTask<T> future : myFutures) {
        JobSchedulerImpl.execute(future);
      }
    }
    finally {
      myLock.unlock();
    }

    // http://gafter.blogspot.com/2006/11/thread-pool-puzzler.html
    for (Future<T> future : myFutures) {
      ((FutureTask)future).run();
    }

    List<T> results = new ArrayList<T>(myFutures.size());

    Throwable ex = null;
    for (Future<T> f : myFutures) {
      try {
        results.add(f.get());
      }
      catch (CancellationException ignore) {
      }
      catch (ExecutionException e) {
        cancel();

        Throwable cause = e.getCause();
        if (cause != null) {
          ex = cause;
        }
      }
    }

    // Future.get() exits when currently running is canceled, thus awaiter may get control before spawned tasks actually terminated,
    // that's why additional join logic.
    for (PrioritizedFutureTask<T> future : myFutures) {
      future.awaitTermination();
    }

    if (ex != null) throw ex;

    return results;
  }

  public void cancel() {
    myLock.lock();
    try {
      if (myCanceled) return;
      myCanceled = true;

      if (myFutures == null) {
        if (JobSchedulerImpl.CORES_COUNT < 2) return; // Futures are not created for single core.
        throw new IllegalStateException("Canceling a job, that haven't been scheduled");
      }

      for (Future<T> future : myFutures) {
        future.cancel(false);
      }
    }
    finally {
      myLock.unlock();
    }
  }

  public boolean isCanceled() {
    return myCanceled;
  }

  public void schedule() {
    myLock.lock();
    try {
      myFutures = new ArrayList<PrioritizedFutureTask<T>>(myTasks.size());

      int startTaskIndex = JobSchedulerImpl.currentTaskIndex();
      for (final Callable<T> task : myTasks) {
        final PrioritizedFutureTask<T> future = new PrioritizedFutureTask<T>(task, myJobIndex, startTaskIndex++, myPriority, false);
        myFutures.add(future);
      }

      for (PrioritizedFutureTask<T> future : myFutures) {
        JobSchedulerImpl.execute(future);
      }
    }
    finally {
      myLock.unlock();
    }
  }

  public boolean isDone() {
    myLock.lock();
    try {
      if (myFutures == null) return false;

      for (Future<T> future : myFutures) {
        if (!future.isDone()) return false;
      }

      return true;
    }
    finally {
      myLock.unlock();
    }
  }
}