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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;

import java.util.*;

/**
* @author Dmitry Avdeev
*/
public class TaskSearchSupport {

  protected NameUtil.Matcher myMatcher;
  private final TaskManagerImpl myManager;
  private final Project myProject;
  private final Comparator<Task> myTaskComparator;
  private final boolean myRemoveLocal;

  public TaskSearchSupport(final Project project, boolean removeLocal) {

    myProject = project;
    myRemoveLocal = removeLocal;
    myManager = (TaskManagerImpl)TaskManager.getManager(myProject);

    myTaskComparator = new Comparator<Task>() {
      public int compare(Task o1, Task o2) {
        int i = Comparing.compare(isOpen(o2, myProject), isOpen(o1, myProject));
        if (i != 0) {
          return i;
        }
        //i = Comparing.compare(o1.isClosed(), o2.isClosed());
        //if (i != 0) {
        //  return i;
        //}
        i = Comparing.compare(o2.getUpdated(), o1.getUpdated());
        return i == 0 ? Comparing.compare(o2.getCreated(), o1.getCreated()) : i;
      }
    };
  }

  private static boolean isOpen(Task task, Project project) {
    return !task.isClosed() && !TaskManager.getManager(project).getOpenChangelists(task).isEmpty();
  }

  public List<Task> getItems(String pattern, boolean cached) {
    final NameUtil.Matcher matcher = getMatcher(pattern);
    return ContainerUtil.mapNotNull(getTasks(pattern, cached), new NullableFunction<Task, Task>() {
      public Task fun(Task task) {
        return matcher.matches(task.getId()) || matcher.matches(task.getSummary()) ? task : null;
      }
    });
  }

  private NameUtil.Matcher getMatcher(String pattern) {
    if (myMatcher == null) {
      StringTokenizer tokenizer = new StringTokenizer(pattern, " ");
      StringBuilder builder = new StringBuilder();
      while (tokenizer.hasMoreTokens()) {
        String word = tokenizer.nextToken();
        builder.append('*');
        builder.append(word);
        builder.append("* ");
      }

      myMatcher = NameUtil.buildMatcher(builder.toString(), 0, true, true, pattern.toLowerCase().equals(pattern));
    }
    return myMatcher;
  }

  private List<Task> getTasks(String pattern, boolean cached) {
    List<Task> issues;
    if (cached) {
      issues = myManager.getCachedIssues();
    }
    else {
      issues = myManager.getIssues(pattern);
    }
    Set<Task> taskSet = new HashSet<Task>(issues);
    if (myRemoveLocal) {
      taskSet.removeAll(Arrays.asList(myManager.getLocalTasks()));
    }
    issues = new ArrayList<Task>(taskSet);
    Collections.sort(issues, myTaskComparator);
    return issues;
  }
}
