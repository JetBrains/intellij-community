/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.tasks.impl;

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

import javax.swing.*;
import java.util.Date;

/**
 * @author Dmitry Avdeev
 *         Date: 12/29/11
 */
public class TaskCheckinHandlerFactory extends CheckinHandlerFactory {

  @NotNull
  @Override
  public CheckinHandler createHandler(@NotNull final CheckinProjectPanel panel, @NotNull final CommitContext commitContext) {
    return new CheckinHandler() {
      @Override
      public void checkinSuccessful() {
        final String message = panel.getCommitMessage();
        if (message != null) {
          final Project project = panel.getProject();
          final TaskManagerImpl manager = (TaskManagerImpl)TaskManager.getManager(project);
          if (manager.getState().saveContextOnCommit) {
            Task task = findTaskInRepositories(message, manager);
            if (task == null) {
              task = manager.createLocalTask(message);
            }
            final LocalTask localTask = manager.addTask(task);
            localTask.setUpdated(new Date());

            //noinspection SSBasedInspection
            SwingUtilities.invokeLater(new Runnable() {
              @Override
              public void run() {
                if (!project.isDisposed()) {
                  WorkingContextManager.getInstance(project).saveContext(localTask);
                }
              }
            });
          }
        }
      }
    };
  }

  @Nullable
  private static Task findTaskInRepositories(String message, TaskManager manager) {
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
