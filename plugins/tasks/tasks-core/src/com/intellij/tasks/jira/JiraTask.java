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

package com.intellij.tasks.jira;

import com.intellij.tasks.*;
import com.intellij.tasks.jira.model.JiraComment;
import com.intellij.tasks.jira.model.JiraIssue;
import com.intellij.tasks.jira.model.JiraIssueType;
import com.intellij.tasks.jira.model.JiraStatus;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import icons.TasksIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Date;

/**
 * @author Dmitry Avdeev
 */
public class JiraTask extends Task {

  private final JiraIssue myJiraIssue;
  private final TaskRepository myRepository;

  public JiraTask(JiraIssue jiraIssue, TaskRepository repository) {
    myJiraIssue = jiraIssue;
    myRepository = repository;
  }

  @NotNull
  public String getId() {
    return myJiraIssue.getKey();
  }

  @NotNull
  public String getSummary() {
    return myJiraIssue.getSummary();
  }

  public String getDescription() {
    return myJiraIssue.getDescription();
  }


  @NotNull
  public Comment[] getComments() {
    return ContainerUtil.map2Array(myJiraIssue.getComments(), Comment.class, new Function<JiraComment, Comment>() {
      @Override
      public Comment fun(JiraComment comment) {
        return new JiraCommentAdapter(comment);
      }
    });
  }

  @NotNull
  public Icon getIcon() {
    JiraIssueType issueType = myJiraIssue.getIssueType();
    String iconUrl = issueType.getIconUrl();
    // iconUrl will be null in JIRA versions prior 5.x.x
    final Icon icon = iconUrl == null
                      ? TasksIcons.Jira
                      : isClosed() ? CachedIconLoader.getDisabledIcon(iconUrl) : CachedIconLoader.getIcon(iconUrl);
    return icon != null ? icon : TasksIcons.Other;
  }

  @NotNull
  @Override
  public TaskType getType() {
    String type = myJiraIssue.getIssueType().getName();
    if (type == null) {
      return TaskType.OTHER;
    }
    else if (type.equals("Bug")) {
      return TaskType.BUG;
    }
    else if (type.equals("Exception")) {
      return TaskType.EXCEPTION;
    }
    else if (type.equals("New Feature")) {
      return TaskType.FEATURE;
    }
    else {
      return TaskType.OTHER;
    }
  }

  @Override
  public TaskState getState() {
    JiraStatus status = myJiraIssue.getStatus();
    switch (Integer.parseInt(status.getId())) {
      case 1:
        return TaskState.OPEN;
      case 3:
        return TaskState.IN_PROGRESS;
      case 4:
        return TaskState.REOPENED;
      case 5: // resolved
      case 6: // closed
        return TaskState.RESOLVED;
    }
    return null;
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

  @Override
  public boolean isClosed() {
    return getState() == TaskState.RESOLVED;
  }

  public boolean isIssue() {
    return true;
  }

  @Override
  public String getIssueUrl() {
    return myRepository.getUrl() + "/browse/" + myJiraIssue.getKey();
  }

  @Nullable
  @Override
  public TaskRepository getRepository() {
    return myRepository;
  }
}
