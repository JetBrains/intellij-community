/*
 * @author max
 */
package com.intellij.concurrency;

import com.intellij.openapi.application.RuntimeInterruptedException;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class PrioritizedFutureTask<T> extends FutureTask<T> implements Comparable<PrioritizedFutureTask> {
  private final long myJobIndex;
  private final int myTaskIndex;
  private final int myPriority;
  private final boolean myParentThreadHasReadAccess;
  private final Lock myLock;
  private volatile Condition myDoneCondition;

  public PrioritizedFutureTask(final Callable<T> callable, long jobIndex, int taskIndex, int priority, final boolean parentThreadHasReadAccess) {
    super(callable);
    myJobIndex = jobIndex;
    myTaskIndex = taskIndex;
    myPriority = priority;
    myParentThreadHasReadAccess = parentThreadHasReadAccess;

    myLock = new ReentrantLock();
  }

  public boolean isParentThreadHasReadAccess() {
    return myParentThreadHasReadAccess;
  }

  public int compareTo(final PrioritizedFutureTask o) {
    if (getPriority() != o.getPriority()) return getPriority() - o.getPriority();
    if (getTaskIndex() != o.getTaskIndex()) return getTaskIndex() - o.getTaskIndex();
    if (getJobIndex() != o.getJobIndex()) return getJobIndex() < o.getJobIndex() ? -1 : 1;
    return 0;
  }

  public long getJobIndex() {
    return myJobIndex;
  }

  public int getTaskIndex() {
    return myTaskIndex;
  }

  public int getPriority() {
    return myPriority;
  }

  public void signalStarted() {
    myLock.lock();
    try {
      myDoneCondition = myLock.newCondition();
    }
    finally {
      myLock.unlock();
    }
  }

  public void signalDone() {
    myLock.lock();
    try {
      myDoneCondition.signalAll();
      myDoneCondition = null;
    }
    finally {
      myLock.unlock();
    }
  }

  public void awaitTermination() {
    myLock.lock();
    try {
      if (myDoneCondition == null) return;
      myDoneCondition.await();
    }
    catch (InterruptedException e) {
      throw new RuntimeInterruptedException(e);
    }
    finally {
      myLock.unlock();
    }
  }
}