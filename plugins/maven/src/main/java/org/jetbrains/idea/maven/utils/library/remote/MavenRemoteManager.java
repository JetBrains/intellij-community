/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.utils.library.remote;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.util.concurrency.FutureResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.ProjectBundle;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public abstract class MavenRemoteManager<Result, Argument, RemoteTask extends MavenRemoteTask<Result, Argument>>
  extends AbstractProjectComponent {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.maven.utils.library.remote.MavenRemoteManager");
  private Deque<DequeItem<Result, Argument, RemoteTask>> tasks = new ArrayDeque<>();
  private boolean busy;

  protected MavenRemoteManager(Project project) {
    super(project);
  }

  public synchronized boolean busy() {
    return busy;
  }

  protected synchronized void schedule(@NotNull RemoteTask task,
                                       @NotNull Argument argument,
                                       RemoteTask.ResultProcessor<Result> resultProcessor,
                                       boolean force) {
    DequeItem<Result, Argument, RemoteTask> dequeItem = new DequeItem<>(task, argument, resultProcessor);
    if (!busy) {
      tasks.addFirst(dequeItem);
      startNextTask();
      return;
    }
    if (force) {
      tasks.addFirst(dequeItem);
    }
    else {
      tasks.addLast(dequeItem);
    }
  }

  protected Future<Result> schedule(@NotNull RemoteTask task, @NotNull Argument argument) {
    final FutureResult<Result> future = new FutureResult<>();
    schedule(task, argument, new MavenRemoteTask.ResultProcessor<Result>() {
      @Override
      public void process(@Nullable Result result) {
        future.set(result);
      }
    }, true);
    return future;
  }

  @Nullable
  protected Result getSynchronously(@NotNull RemoteTask task, @NotNull Argument argument) {
    try {
      return schedule(task, argument).get();
    }
    catch (InterruptedException e) {
      LOG.error("Got unexpected exception during maven remote task", e);
    }
    catch (ExecutionException e) {
      LOG.error("Got unexpected exception during maven remote task", e);
    }
    return null;
  }

  @Nullable
  protected Result getSynchronouslyWithModal(@NotNull final RemoteTask task, @NotNull final Argument argument, String title) {
    final Ref<Result> result = Ref.create();
    new Task.Modal(myProject, title, false) {
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setText(task.getName(argument));
        result.set(getSynchronously(task, argument));
      }
    }.queue();
    return result.get();
  }

  private synchronized void startNextTask() {
    busy = true;
    if (ApplicationManager.getApplication().isDispatchThread()) {
      startTask();
    }
    else {
      ApplicationManager.getApplication().invokeLater(() -> startTask(), ModalityState.any());
    }
  }

  private synchronized void startTask() {
    final DequeItem<Result, Argument, RemoteTask> item = tasks.pollFirst();
    busy = item != null;
    if (item == null) {
      return;
    }

    new Task.Backgroundable(myProject, "Maven", false) {
      public void run(@NotNull ProgressIndicator indicator) {
        String taskName = item.getTask().getName(item.getArgument());
        indicator.setText(taskName);
        Result result = null;
        try {
          result = item.getTask().execute(item.getArgument(), indicator);
        }
        catch (ProcessCanceledException exc) {
          LOG.info(ProjectBundle.message("maven.remote.task.cancelled", taskName));
        }
        catch (Exception exc) {
          LOG.error(ProjectBundle.message("maven.remote.task.failed", taskName), exc);
        }

        MavenRemoteTask.ResultProcessor<Result> resultProcessor = item.getResultProcessor();
        if (resultProcessor != null) {
          resultProcessor.process(result);
        }
        startNextTask();
      }
    }.queue();
  }

  private static class DequeItem<Result, Argument, Task extends MavenRemoteTask<Result, Argument>> {
    @NotNull private final Task task;
    @NotNull private final Argument argument;
    @Nullable private final Task.ResultProcessor<Result> resultProcessor;


    private DequeItem(@NotNull Task task, @NotNull Argument argument, @Nullable MavenRemoteTask.ResultProcessor<Result> processor) {
      this.task = task;
      this.argument = argument;
      resultProcessor = processor;
    }

    @NotNull
    public Task getTask() {
      return task;
    }

    @NotNull
    public Argument getArgument() {
      return argument;
    }

    @Nullable
    public Task.ResultProcessor<Result> getResultProcessor() {
      return resultProcessor;
    }
  }
}
