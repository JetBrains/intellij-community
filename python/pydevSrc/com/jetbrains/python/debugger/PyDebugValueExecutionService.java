// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.ConcurrencyUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class PyDebugValueExecutionService {
  @Nullable private ExecutorService myAsyncValuesExecutorService;
  @NotNull private final List<PyFrameAccessor> myFrameAccessors;
  @NotNull private final List<List<Future>> mySubmittedTasks;

  public static PyDebugValueExecutionService getInstance(Project project) {
    return ServiceManager.getService(project, PyDebugValueExecutionService.class);
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
    List<Future> submittedTasks = mySubmittedTasks.get(i);
    for (Future task : submittedTasks) {
      if (!task.isDone()) {
        task.cancel(true);
      }
    }
    submittedTasks.clear();
  }

  public synchronized void sessionStopped(@NotNull PyFrameAccessor frameAccessor) {
    int i = myFrameAccessors.indexOf(frameAccessor);
    if (i != -1) {
      myFrameAccessors.remove(i);
      mySubmittedTasks.remove(i);
    }
    if (myFrameAccessors.size() == 0 && myAsyncValuesExecutorService != null) {
      myAsyncValuesExecutorService.shutdownNow();
      myAsyncValuesExecutorService = null;
    }
  }
}
