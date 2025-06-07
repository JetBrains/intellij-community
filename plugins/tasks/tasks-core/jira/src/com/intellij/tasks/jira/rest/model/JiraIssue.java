// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.jira.rest.model;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
public abstract class JiraIssue {
  @Override
  public String toString() {
    return String.format("JiraIssue(key=%s, summary='%s')", getKey(), getSummary());
  }

  public abstract @NotNull String getKey();

  public abstract @NotNull String getIssueUrl();

  public abstract @NotNull @NlsSafe String getSummary();

  public abstract @Nullable @NlsSafe String getDescription();

  public abstract @NotNull Date getCreated();

  public abstract @NotNull Date getUpdated();

  public abstract @Nullable Date getResolutionDate();

  public abstract @Nullable Date getDueDate();

  public abstract @NotNull JiraIssueType getIssueType();

  public abstract @Nullable JiraUser getAssignee();

  public abstract @Nullable JiraUser getReporter();

  public abstract @NotNull List<JiraComment> getComments();

  public abstract @NotNull JiraStatus getStatus();
}
