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

  protected class LongRunningTask implements AutoCloseable {
    @NotNull private final String myId;
    private final AtomicInteger myFinishedRequests = new AtomicInteger(0);
    private final AtomicInteger myTotalRequests;
    private final AtomicBoolean isCanceled = new AtomicBoolean(false);

    protected LongRunningTask(@NotNull String id, int totalRequests) {
      myId = id;
      myTotalRequests = new AtomicInteger(totalRequests);

      myLongRunningTasks.put(myId, this);
    }

    public void incrementFinishedRequests() {
      myFinishedRequests.incrementAndGet();
    }

    private int getFinishedRequests() {
      return myFinishedRequests.get();
    }

    private int getTotalRequests() {
      return myTotalRequests.get();
    }

    public void updateTotalRequests(int newValue) {
      myTotalRequests.set(newValue);
    }

    private void cancel() {
      isCanceled.set(true);
    }

    public boolean isCanceled() {
      return isCanceled.get();
    }

    @Override
    public void close() {
      myLongRunningTasks.remove(myId);
    }
  }
}
