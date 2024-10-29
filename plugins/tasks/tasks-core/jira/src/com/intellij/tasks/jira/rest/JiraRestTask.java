// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.tasks.jira.rest;

import com.intellij.tasks.Comment;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.TaskState;
import com.intellij.tasks.TaskType;
import com.intellij.tasks.jira.JiraTask;
import com.intellij.tasks.jira.rest.model.JiraIssue;
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
  public Comment @NotNull [] getComments() {
    return ContainerUtil.map2Array(myJiraIssue.getComments(), Comment.class, comment -> new Comment() {

      @Override
      public String getText() {
        return comment.getBody();
      }

      @Override
      public String getAuthor() {
        return comment.getAuthor().getDisplayName();
      }

      @Override
      public Date getDate() {
        return comment.getCreated();
      }

      @Override
      public String toString() {
        return comment.getAuthor().getDisplayName();
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
