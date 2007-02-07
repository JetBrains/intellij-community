/*
 * @author max
 */
package com.intellij.concurrency;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

public class PrioritizedFutureTask<T> extends FutureTask<T> implements Comparable<PrioritizedFutureTask> {
  private final long myJobIndex;
  private final int myTaskIndex;
  private final int myPriority;
  private final boolean myParentThreadHasReadAccess;

  public PrioritizedFutureTask(final Callable<T> callable, long jobIndex, int taskIndex, int priority, final boolean parentThreadHasReadAccess) {
    super(callable);
    myJobIndex = jobIndex;
    myTaskIndex = taskIndex;
    myPriority = priority;
    myParentThreadHasReadAccess = parentThreadHasReadAccess;
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
}