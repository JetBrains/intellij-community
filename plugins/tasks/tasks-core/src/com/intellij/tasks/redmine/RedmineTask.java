// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.redmine;

import com.intellij.tasks.Comment;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.TaskType;
import com.intellij.tasks.redmine.model.RedmineIssue;
import com.intellij.tasks.redmine.model.RedmineProject;
import icons.TasksCoreIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Date;

/**
 * @author Mikhail Golubev
 */
public class RedmineTask extends Task {
  private final RedmineIssue myIssue;
  private final RedmineRepository myRepository;
  /**
   * Only human-readable project name is sent with issue. Because project's identifier is more suited
   * for commit messages, it has to be extracted from cached projects. The same approach is used in
   * {@link com.intellij.tasks.gitlab.GitlabRepository}.
   */
  private final RedmineProject myProject;

  public RedmineTask(@NotNull RedmineRepository repository, @NotNull RedmineIssue issue) {
    myIssue = issue;
    myRepository = repository;
    RedmineProject project = null;
    for (RedmineProject p : repository.getProjects()) {
      if (issue.getProject() != null && p.getId() == issue.getProject().getId()) {
        project = p;
        break;
      }
    }
    myProject = project;
  }

  @Override
  public @NotNull String getId() {
    return String.valueOf(myIssue.getId());
  }

  @Override
  public @NotNull String getSummary() {
    return myIssue.getSubject();
  }

  @Override
  public @Nullable String getDescription() {
    return myIssue.getDescription();
  }

  @Override
  public Comment @NotNull [] getComments() {
    return Comment.EMPTY_ARRAY;
  }

  @Override
  public @NotNull Icon getIcon() {
    return TasksCoreIcons.Redmine;
  }

  @Override
  public @NotNull TaskType getType() {
    // TODO: precise mapping
    return TaskType.BUG;
  }

  @Override
  public @Nullable Date getUpdated() {
    return myIssue.getUpdated();
  }

  @Override
  public @Nullable Date getCreated() {
    return myIssue.getCreated();
  }

  @Override
  public boolean isClosed() {
    String name = myIssue.getStatus().getName();
    return name.equals("Closed") || name.equals("Resolved");
  }

  @Override
  public boolean isIssue() {
    return true;
  }

  @Override
  public @Nullable String getIssueUrl() {
    return myRepository.getRestApiUrl("issues", getId());
  }

  @Override
  public @NotNull String getNumber() {
    return getId();
  }

  @Override
  public @Nullable String getProject() {
    return myProject == null ? null : myProject.getIdentifier();
  }

  @Override
  public @Nullable TaskRepository getRepository() {
    return myRepository;
  }
}
