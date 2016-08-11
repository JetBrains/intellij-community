/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.intellij.tasks.actions;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.Matcher;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * @author Dmitry Avdeev
 */
public class TaskSearchSupport {
  private TaskSearchSupport() {
  }

  public static List<Task> getLocalAndCachedTasks(final TaskManager myManager, String pattern, final boolean withClosed) {
    List<Task> tasks = new ArrayList<>();
    ContainerUtil.addAll(tasks, myManager.getLocalTasks(withClosed));
    ContainerUtil.addAll(tasks, ContainerUtil.filter(myManager.getCachedIssues(withClosed),
                                                     task -> myManager.findTask(task.getId()) == null));
    List<Task> filteredTasks = filterTasks(pattern, tasks);
    ContainerUtil.sort(filteredTasks, TaskManagerImpl.TASK_UPDATE_COMPARATOR);
    return filteredTasks;
  }

  public static List<Task> filterTasks(final String pattern, final List<Task> tasks) {
    final Matcher matcher = getMatcher(pattern);
    return ContainerUtil.mapNotNull(tasks,
                                    (NullableFunction<Task, Task>)task -> matcher.matches(task.getPresentableId()) || matcher.matches(task.getSummary()) ? task : null);
  }

  public static List<Task> getRepositoriesTasks(final TaskManager myManager,
                                                String pattern,
                                                int offset,
                                                int limit,
                                                boolean forceRequest,
                                                final boolean withClosed,
                                                @NotNull final ProgressIndicator cancelled) {
    List<Task> tasks = myManager.getIssues(pattern, offset, limit, withClosed, cancelled, forceRequest);
    ContainerUtil.sort(tasks, TaskManagerImpl.TASK_UPDATE_COMPARATOR);
    return tasks;
  }

  public static List<Task> getItems(final TaskManager myManager,
                                    String pattern,
                                    boolean cached,
                                    boolean autopopup) {
    return filterTasks(pattern, getTasks(pattern, cached, autopopup, myManager));
  }


  private static Matcher getMatcher(String pattern) {
    StringTokenizer tokenizer = new StringTokenizer(pattern, " ");
    StringBuilder builder = new StringBuilder();
    while (tokenizer.hasMoreTokens()) {
      String word = tokenizer.nextToken();
      builder.append('*');
      builder.append(word);
      builder.append("* ");
    }

    return NameUtil.buildMatcher(builder.toString(), NameUtil.MatchingCaseSensitivity.NONE);
  }

  private static List<Task> getTasks(String pattern, boolean cached, boolean autopopup, final TaskManager myManager) {
    return cached ? myManager.getCachedIssues() : myManager.getIssues(pattern, !autopopup);
  }
}
