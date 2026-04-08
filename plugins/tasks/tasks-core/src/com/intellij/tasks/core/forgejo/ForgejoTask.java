// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.core.forgejo;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.tasks.Comment;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.TaskType;
import com.intellij.tasks.core.forgejo.model.ForgejoIssue;
import com.intellij.tasks.core.forgejo.model.ForgejoProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.Date;

public class ForgejoTask extends Task {
  private final ForgejoIssue myIssue;
  private final ForgejoRepository myRepository;

  public ForgejoTask(@NotNull ForgejoRepository repository, @NotNull ForgejoIssue issue) {
    myRepository = repository;
    myIssue = issue;
  }

  @Override
  public @NotNull String getId() {
    return String.valueOf(myIssue.getId());
  }

  @Override
  public @NotNull String getPresentableId() {
    return "#" + myIssue.getNumber();
  }

  @Override
  public @NotNull String getSummary() {
    return myIssue.getTitle();
  }

  @Override
  public @Nullable @NlsSafe String getDescription() {
    return myIssue.getBody();
  }

  @Override
  public Comment @NotNull [] getComments() {
    return Comment.EMPTY_ARRAY;
  }

  @Override
  public @NotNull Icon getIcon() {
    // TODO: replace with Forgejo icon
    return AllIcons.Actions.Stub;
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
    return String.valueOf(myIssue.getNumber());
  }

  @Override
  public @Nullable String getProject() {
    ForgejoProject currentProject = myRepository.getCurrentProject();
    if (currentProject != null && currentProject.getFullName() != null) {
      return currentProject.getFullName();
    }
    ForgejoProject issueRepo = myIssue.getRepository();
    if (issueRepo != null) {
      return issueRepo.getFullName();
    }
    return null;
  }

  @Override
  public @Nullable String getIssueUrl() {
    return myIssue.getHtmlUrl();
  }

  @Override
  public @Nullable TaskRepository getRepository() {
    return myRepository;
  }
}
