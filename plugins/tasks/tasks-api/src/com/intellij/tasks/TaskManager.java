/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.tasks;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public abstract class TaskManager {

  public static TaskManager getManager(@NotNull Project project) {
    return project.getComponent(TaskManager.class);
  }

  public enum VcsOperation {
    CREATE_BRANCH,
    CREATE_CHANGELIST,
    DO_NOTHING
  }

  /**
   * Queries all configured task repositories.
   * Operation may be blocked for a while.
   * @param query text search
   * @return up-to-date issues retrieved from repositories
   * @see #getCachedIssues()
   */
  public abstract List<Task> getIssues(@Nullable String query);

  public abstract List<Task> getIssues(@Nullable String query, boolean forceRequest);

  public abstract List<Task> getIssues(@Nullable String query,
                                       int max,
                                       long since,
                                       boolean forceRequest,
                                       final boolean withClosed,
                                       @NotNull final ProgressIndicator cancelled);
  /**
   * Returns already cached issues.
   * @return cached issues.
   */
  public abstract List<Task> getCachedIssues();

  public abstract List<Task> getCachedIssues(final boolean withClosed);

  @Nullable
  public abstract Task updateIssue(@NotNull String id);

  public abstract List<LocalTask> getLocalTasks();

  public abstract List<LocalTask> getLocalTasks(final boolean withClosed);

  public abstract LocalTask addTask(Task issue);

  public abstract LocalTask createLocalTask(String summary);

  public abstract LocalTask activateTask(@NotNull Task task, boolean clearContext);

  @NotNull
  public abstract LocalTask getActiveTask();

  @Nullable
  public abstract LocalTask findTask(String id);

  /**
   * Update issue cache asynchronously
   * @param onComplete callback to be invoked after updating
   */
  public abstract void updateIssues(@Nullable Runnable onComplete);

  public abstract boolean isVcsEnabled();

  public abstract AbstractVcs getActiveVcs();

  public abstract boolean isLocallyClosed(LocalTask localTask);

  @Nullable
  public abstract LocalTask getAssociatedTask(LocalChangeList list);

  public abstract void trackContext(LocalChangeList changeList);

  public abstract void disassociateFromTask(LocalChangeList changeList);

  public abstract void removeTask(LocalTask task);

  public abstract void addTaskListener(TaskListener listener);

  public abstract void removeTaskListener(TaskListener listener);
  // repositories management

  public abstract TaskRepository[] getAllRepositories();

  public abstract boolean testConnection(TaskRepository repository);

}
