// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.tasks.actions;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskBundle;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.config.TaskRepositoriesConfigurable;
import com.intellij.tasks.impl.RequestFailedException;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.tasks.impl.TaskUtil.filterTasks;

/**
 * @author Dmitry Avdeev
 */
public final class TaskSearchSupport {
  private TaskSearchSupport() {
  }

  public static List<Task> getLocalAndCachedTasks(final TaskManager myManager, String pattern, final boolean withClosed) {
    List<Task> tasks = new ArrayList<>();
    tasks.addAll(myManager.getLocalTasks(withClosed));
    tasks.addAll(ContainerUtil.filter(myManager.getCachedIssues(withClosed),
                                                     task -> myManager.findTask(task.getId()) == null));
    List<Task> filteredTasks = ContainerUtil.sorted(filterTasks(pattern, tasks),
    TaskManagerImpl.TASK_UPDATE_COMPARATOR);
    return filteredTasks;
  }

  public static List<Task> getRepositoriesTasks(Project project,
                                                String pattern,
                                                int offset,
                                                int limit,
                                                boolean forceRequest,
                                                final boolean withClosed,
                                                @NotNull final ProgressIndicator cancelled) {
    try {
      TaskManager manager = TaskManager.getManager(project);
      List<Task> tasks = manager.getIssues(pattern, offset, limit, withClosed, cancelled, forceRequest);
      ContainerUtil.sort(tasks, TaskManagerImpl.TASK_UPDATE_COMPARATOR);
      return tasks;
    }
    catch (RequestFailedException e) {
      notifyAboutConnectionFailure(e, project);
      return Collections.emptyList();
    }
  }

  public static List<Task> getItems(final TaskManager myManager,
                                    String pattern,
                                    boolean cached,
                                    boolean autopopup) {
    return filterTasks(pattern, getTasks(pattern, cached, autopopup, myManager));
  }

  private static List<Task> getTasks(String pattern, boolean cached, boolean autopopup, final TaskManager myManager) {
    return cached ? myManager.getCachedIssues() : myManager.getIssues(pattern, !autopopup);
  }

  static final String TASKS_NOTIFICATION_GROUP = "Task Group";

  private static void notifyAboutConnectionFailure(RequestFailedException e, Project project) {
    String details = e.getMessage();
    TaskRepository repository = e.getRepository();
    String content = TaskBundle.message("notification.content.p.href.configure.server.p");
    if (!StringUtil.isEmpty(details)) {
      content = "<p>" + details + "</p>" + content; //NON-NLS
    }
    new Notification(TASKS_NOTIFICATION_GROUP, TaskBundle.message("notification.title.cannot.connect.to", repository.getUrl()), content, NotificationType.WARNING)
      .setListener((notification, event) -> {
        TaskRepositoriesConfigurable configurable = new TaskRepositoriesConfigurable(project);
        ShowSettingsUtil.getInstance().editConfigurable(project, configurable);
        if (!ArrayUtil.contains(repository, TaskManager.getManager(project).getAllRepositories())) {
          notification.expire();
        }
      })
      .notify(project);
  }
}
