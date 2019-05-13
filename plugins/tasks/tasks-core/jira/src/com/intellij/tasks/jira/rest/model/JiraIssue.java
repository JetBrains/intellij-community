package com.intellij.tasks.jira.rest.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
public abstract class JiraIssue {
  public String toString() {
    return String.format("JiraIssue(key=%s, summary='%s')", getKey(), getSummary());
  }

  @NotNull
  public abstract String getKey();

  @NotNull
  public abstract String getIssueUrl();

  @NotNull
  public abstract String getSummary();

  @Nullable
  public abstract String getDescription();

  @NotNull
  public abstract Date getCreated();

  @NotNull
  public abstract Date getUpdated();

  @Nullable
  public abstract Date getResolutionDate();

  @Nullable
  public abstract Date getDueDate();

  @NotNull
  public abstract JiraIssueType getIssueType();

  @Nullable
  public abstract JiraUser getAssignee();

  @Nullable
  public abstract JiraUser getReporter();

  @NotNull
  public abstract List<JiraComment> getComments();

  @NotNull
  public abstract JiraStatus getStatus();
}
