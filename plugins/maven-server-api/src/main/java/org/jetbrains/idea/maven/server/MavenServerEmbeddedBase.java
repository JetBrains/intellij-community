// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.server.security.MavenToken;

import java.rmi.RemoteException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class MavenServerEmbeddedBase extends MavenRemoteObject implements MavenServerEmbedder {
  private final Map<String, LongRunningTask> myLongRunningTasks = new ConcurrentHashMap<>();

  @NotNull
  @Override
  public LongRunningTaskStatus getLongRunningTaskStatus(@NotNull String longRunningTaskId, MavenToken token) {
    MavenServerUtil.checkToken(token);

    LongRunningTask task = myLongRunningTasks.get(longRunningTaskId);

    if (null == task) return new LongRunningTaskStatus(0, 0);

    return new LongRunningTaskStatus(task.getTotalRequests(), task.getFinishedRequests());
  }

  @Override
  public boolean cancelLongRunningTask(@NotNull String longRunningTaskId, MavenToken token) throws RemoteException {
    MavenServerUtil.checkToken(token);

    LongRunningTask task = myLongRunningTasks.get(longRunningTaskId);

    if (null == task) return false;

    task.cancel();
    return true;
  }

  public interface LongRunningTask extends AutoCloseable {
    void incrementFinishedRequests();

    int getFinishedRequests();

    int getTotalRequests();

    void updateTotalRequests(int newValue);

    void cancel();

    boolean isCanceled();

    @Override
    void close();
  }

  @NotNull
  protected LongRunningTask newLongRunningTask(@NotNull String id, int totalRequests) {
    return new LongRunningTaskImpl(id, totalRequests);
  }

  protected class LongRunningTaskImpl implements LongRunningTask {
    @NotNull private final String myId;
    private final AtomicInteger myFinishedRequests = new AtomicInteger(0);
    private final AtomicInteger myTotalRequests;
    private final AtomicBoolean isCanceled = new AtomicBoolean(false);

    public LongRunningTaskImpl(@NotNull String id, int totalRequests) {
      myId = id;
      myTotalRequests = new AtomicInteger(totalRequests);

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
    }
  }
}
