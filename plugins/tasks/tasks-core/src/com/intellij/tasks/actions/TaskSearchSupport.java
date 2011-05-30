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
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.Matcher;

import java.util.List;
import java.util.StringTokenizer;

/**
* @author Dmitry Avdeev
*/
public class TaskSearchSupport {
  protected Matcher myMatcher;
  private final TaskManagerImpl myManager;

  public TaskSearchSupport(final Project project) {
    myManager = (TaskManagerImpl)TaskManager.getManager(project);
  }

  public List<Task> getItems(String pattern, boolean cached) {
    final Matcher matcher = getMatcher(pattern);
    return ContainerUtil.mapNotNull(getTasks(pattern, cached), new NullableFunction<Task, Task>() {
      public Task fun(Task task) {
        return matcher.matches(task.getId()) || matcher.matches(task.getSummary()) ? task : null;
      }
    });
  }

  private Matcher getMatcher(String pattern) {
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
    return cached ? myManager.getCachedIssues() : myManager.getIssues(pattern);
  }
}
