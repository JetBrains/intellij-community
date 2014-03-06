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

package com.intellij.tasks.jira.rest;

import com.intellij.tasks.Comment;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.TaskState;
import com.intellij.tasks.TaskType;
import com.intellij.tasks.jira.JiraTask;
import com.intellij.tasks.jira.rest.model.JiraComment;
import com.intellij.tasks.jira.rest.model.JiraIssue;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

/**
 * @author Dmitry Avdeev
 */
public class JiraRestTask extends JiraTask {

  private final JiraIssue myJiraIssue;

  public JiraRestTask(JiraIssue jiraIssue, TaskRepository repository) {
    super(repository);
    myJiraIssue = jiraIssue;
  }

  @Override
  @NotNull
  public String getId() {
    return myJiraIssue.getKey();
  }

  @Override
  @NotNull
  public String getSummary() {
    return myJiraIssue.getSummary();
  }

  @Override
  public String getDescription() {
    return myJiraIssue.getDescription();
  }


  @Override
  @NotNull
  public Comment[] getComments() {
    return ContainerUtil.map2Array(myJiraIssue.getComments(), Comment.class, new Function<JiraComment, Comment>() {
      @Override
      public Comment fun(final JiraComment comment) {
        return new Comment() {

          public String getText() {
            return comment.getBody();
          }

          public String getAuthor() {
            return comment.getAuthor().getDisplayName();
          }

          public Date getDate() {
            return comment.getCreated();
          }

          @Override
          public String toString() {
            return comment.getAuthor().getDisplayName();
          }
        };
      }
    });
  }

  @Override
  @Nullable
  protected String getIconUrl() {
    // iconUrl will be null in JIRA versions prior 5.x.x
    return myJiraIssue.getIssueType().getIconUrl();
  }

  @NotNull
  @Override
  public TaskType getType() {
    return getTypeByName(myJiraIssue.getIssueType().getName());
  }

  @Override
  public TaskState getState() {
    return getStateById(Integer.parseInt(myJiraIssue.getStatus().getId()));
  }

  @Nullable
  @Override
  public Date getUpdated() {
    return myJiraIssue.getUpdated();
  }

  @Override
  public Date getCreated() {
    return myJiraIssue.getCreated();
  }

  public JiraIssue getJiraIssue() {
    return myJiraIssue;
  }
}
