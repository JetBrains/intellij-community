// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.gitlab;

import com.intellij.tasks.Comment;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.TaskType;
import com.intellij.tasks.gitlab.model.GitlabIssue;
import com.intellij.tasks.gitlab.model.GitlabProject;
import icons.TasksCoreIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Date;

/**
 * @author Mikhail Golubev
 */
public class GitlabTask extends Task {
  private final GitlabIssue myIssue;
  private final GitlabRepository myRepository;
  private final GitlabProject myProject;

  public GitlabTask(@NotNull GitlabRepository repository, @NotNull GitlabIssue issue) {
    myRepository = repository;
    myIssue = issue;

    GitlabProject project = null;
    for (GitlabProject p : myRepository.getProjects()) {
      if (p.getId() == myIssue.getProjectId()) {
        project = p;
      }
    }
    myProject = project;
  }

  @Override
  public @NotNull String getId() {
    // Will be in form <projectId>:<issueId>
    //return myIssue.getProjectId() + ":" + myIssue.getId();
    return String.valueOf(myIssue.getId());
  }

  @Override
  public @NotNull String getPresentableId() {
    return "#" + myIssue.getLocalId();
  }

  @Override
  public @NotNull String getSummary() {
    return myIssue.getTitle();
  }

  @Override
  public @Nullable String getDescription() {
    return null;
  }

  @Override
  public Comment @NotNull [] getComments() {
    return Comment.EMPTY_ARRAY;
  }

  @Override
  public @NotNull Icon getIcon() {
    return TasksCoreIcons.Gitlab;
  }

  @Override
  public @NotNull TaskType getType() {
    return TaskType.BUG;
  }

  @Override
  public @Nullable Date getUpdated() {
    return myIssue.getUpdatedAt();
  }

  @Override
  public @Nullable Date getCreated() {
    return myIssue.getCreatedAt();
  }

  @Override
  public boolean isClosed() {
    return myIssue.getState().equals("closed");
  }

  @Override
  public boolean isIssue() {
    return true;
  }

  @Override
  public @NotNull String getNumber() {
    return String.valueOf(myIssue.getLocalId());
  }

  @Override
  public @Nullable String getProject() {
    return myProject == null ? null : myProject.getName();
  }

  @Override
  public @Nullable String getIssueUrl() {
    if (myProject != null) {
      return myProject.getWebUrl() + "/issues/" + myIssue.getLocalId();
    }
    return null;
  }

  @Override
  public @Nullable TaskRepository getRepository() {
    return myRepository;
  }
}
