// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.server.security.MavenToken;

import java.rmi.RemoteException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class MavenServerEmbeddedBase extends MavenRemoteObject implements MavenServerEmbedder {
  private final Map<String, LongRunningTaskImpl> myLongRunningTasks = new ConcurrentHashMap<>();

  @Override
  public @NotNull LongRunningTaskStatus getLongRunningTaskStatus(@NotNull String longRunningTaskId, MavenToken token) {
    MavenServerUtil.checkToken(token);

    LongRunningTaskImpl task = myLongRunningTasks.get(longRunningTaskId);

    if (null == task) return LongRunningTaskStatus.EMPTY;

    return new LongRunningTaskStatus(
      task.getTotalRequests(),
      task.getFinishedRequests(),
      task.getIndicator().pullConsoleEvents(),
      task.getIndicator().pullDownloadEvents()
    );
  }

  @Override
  public boolean cancelLongRunningTask(@NotNull String longRunningTaskId, MavenToken token) throws RemoteException {
    MavenServerUtil.checkToken(token);

    LongRunningTask task = myLongRunningTasks.get(longRunningTaskId);

    if (null == task) return false;

    task.cancel();
    return true;
  }

  protected @NotNull LongRunningTask newLongRunningTask(@NotNull String id,
                                                        int totalRequests,
                                                        @NotNull MavenServerConsoleIndicatorWrapper indicatorWrapper) {
    return new LongRunningTaskImpl(id, totalRequests, indicatorWrapper);
  }

  protected class LongRunningTaskImpl implements LongRunningTask {
    private final @NotNull String myId;
    private final AtomicInteger myFinishedRequests = new AtomicInteger(0);
    private final AtomicInteger myTotalRequests;
    private final AtomicBoolean isCanceled = new AtomicBoolean(false);

    private final @NotNull MavenServerConsoleIndicatorImpl myIndicator;

    private final @NotNull MavenServerConsoleIndicatorWrapper myIndicatorWrapper;

    public LongRunningTaskImpl(@NotNull String id, int totalRequests, @NotNull MavenServerConsoleIndicatorWrapper indicatorWrapper) {
      myId = id;
      myTotalRequests = new AtomicInteger(totalRequests);

      myIndicator = new MavenServerConsoleIndicatorImpl();

      myIndicatorWrapper = indicatorWrapper;
      myIndicatorWrapper.setWrappee(myIndicator);

      myLongRunningTasks.put(myId, this);
    }

    @Override
    public void incrementFinishedRequests() {
      myFinishedRequests.incrementAndGet();
    }

    @Override
    public int getFinishedRequests() {
      return myFinishedRequests.get();
    }

    @Override
    public int getTotalRequests() {
      return myTotalRequests.get();
    }

    @Override
    public void updateTotalRequests(int newValue) {
      myTotalRequests.set(newValue);
    }

    @Override
    public void cancel() {
      isCanceled.set(true);
    }

    @Override
    public boolean isCanceled() {
      return isCanceled.get();
    }

    @Override
    public void close() {
      myLongRunningTasks.remove(myId);
      myIndicatorWrapper.setWrappee(null);
    }

    @Override
    @NotNull
    public MavenServerConsoleIndicatorImpl getIndicator() {
      return myIndicator;
    }
  }

  @Override
  public boolean ping(MavenToken token) throws RemoteException {
    MavenServerUtil.checkToken(token);
    return true;
  }
}
