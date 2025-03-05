// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.util.ConcurrencyUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Service(Service.Level.PROJECT)
public final class PyDebugValueExecutionService {
  private @Nullable ExecutorService myAsyncValuesExecutorService;
  private final @NotNull List<PyFrameAccessor> myFrameAccessors;
  private final @NotNull List<List<Future>> mySubmittedTasks;

  public static PyDebugValueExecutionService getInstance(Project project) {
    return project.getService(PyDebugValueExecutionService.class);
  }

  private PyDebugValueExecutionService() {
    myAsyncValuesExecutorService =
      Executors.newFixedThreadPool(PyDebugValue.AVAILABLE_PROCESSORS, ConcurrencyUtil.newNamedThreadFactory("PyDebug Async Executor"));
    myFrameAccessors = new ArrayList<>();
    mySubmittedTasks = new ArrayList<>();
  }

  public synchronized void sessionStarted(@NotNull PyFrameAccessor frameAccessor) {
    if (myAsyncValuesExecutorService == null) {
      myAsyncValuesExecutorService =
        Executors.newFixedThreadPool(PyDebugValue.AVAILABLE_PROCESSORS, ConcurrencyUtil.newNamedThreadFactory("PyDebug Async Executor"));
    }
    if (!myFrameAccessors.contains(frameAccessor)) {
      myFrameAccessors.add(frameAccessor);
      mySubmittedTasks.add(new ArrayList<>());
    }
  }

  public synchronized void submitTask(@NotNull PyFrameAccessor frameAccessor, @NotNull Runnable task) {
    Future<?> future = myAsyncValuesExecutorService != null ? myAsyncValuesExecutorService.submit(task) : null;
    if (!myFrameAccessors.contains(frameAccessor)) {
      myFrameAccessors.add(frameAccessor);
      mySubmittedTasks.add(new ArrayList<>());
    }
    int i = myFrameAccessors.indexOf(frameAccessor);
    mySubmittedTasks.get(i).add(future);
  }

  public synchronized void cancelSubmittedTasks(@NotNull PyFrameAccessor frameAccessor) {
    int i = myFrameAccessors.indexOf(frameAccessor);
    if (i != -1) {
      List<Future> submittedTasks = mySubmittedTasks.get(i);
      for (Future task : submittedTasks) {
        if (!task.isDone()) {
          task.cancel(true);
        }
      }
      submittedTasks.clear();
    }
  }

  public synchronized void sessionStopped(@NotNull PyFrameAccessor frameAccessor) {
    int i = myFrameAccessors.indexOf(frameAccessor);
    if (i != -1) {
      myFrameAccessors.remove(i);
      mySubmittedTasks.remove(i);
    }
    if (myFrameAccessors.isEmpty() && myAsyncValuesExecutorService != null) {
      myAsyncValuesExecutorService.shutdownNow();
      myAsyncValuesExecutorService = null;
    }
  }

  @TestOnly
  public synchronized void shutDownNow(long timeout) throws InterruptedException {
    if (myAsyncValuesExecutorService != null) {
      myAsyncValuesExecutorService.shutdownNow();
      myAsyncValuesExecutorService.awaitTermination(timeout, TimeUnit.MILLISECONDS);
    }
  }
}
