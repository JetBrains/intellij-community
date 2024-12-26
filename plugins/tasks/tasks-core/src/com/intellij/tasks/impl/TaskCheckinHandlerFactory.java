// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.context.WorkingContextManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

public class TaskCheckinHandlerFactory extends CheckinHandlerFactory {

  @Override
  public @NotNull CheckinHandler createHandler(final @NotNull CheckinProjectPanel panel, final @NotNull CommitContext commitContext) {
    return new CheckinHandler() {
      @Override
      public void checkinSuccessful() {
        final String message = panel.getCommitMessage();
        final Project project = panel.getProject();
        final TaskManagerImpl manager = (TaskManagerImpl)TaskManager.getManager(project);
        if (manager.getState().saveContextOnCommit) {
          Task task = findTaskInRepositories(message, manager);
          if (task == null) {
            task = manager.createLocalTask(message);
            ((LocalTaskImpl)task).setClosed(true);
          }
          LocalTask localTask = manager.addTask(task);
          localTask.setUpdated(new Date());

          ApplicationManager.getApplication().invokeLater(() -> WorkingContextManager.getInstance(project).saveContext(localTask), project.getDisposed());
        }
      }
    };
  }

  private static @Nullable Task findTaskInRepositories(String message, TaskManager manager) {
    TaskRepository[] repositories = manager.getAllRepositories();
    for (TaskRepository repository : repositories) {
      String id = repository.extractId(message);
      if (id == null) continue;
      LocalTask localTask = manager.findTask(id);
      if (localTask != null) return localTask;
      try {
        Task task = repository.findTask(id);
        if (task != null) {
          return task;
        }
      }
      catch (Exception ignore) {

      }
    }
    return null;
  }
}
