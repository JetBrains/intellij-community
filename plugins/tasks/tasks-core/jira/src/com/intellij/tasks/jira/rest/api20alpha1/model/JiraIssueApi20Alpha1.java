// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.jira.rest.api20alpha1.model;

import com.intellij.tasks.jira.rest.model.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class JiraIssueApi20Alpha1 extends JiraIssue {
  private Fields fields;
  private String self;
  private String key;


  @Override
  public @NotNull String getKey() {
    return key;
  }

  @Override
  public @NotNull String getIssueUrl() {
    return self;
  }

  @Override
  public @NotNull String getSummary() {
    return fields.summary.getValue();
  }

  @Override
  public @Nullable String getDescription() {
    return fields.description.getValue();
  }

  @Override
  public @NotNull Date getCreated() {
    return fields.created.getValue();
  }

  @Override
  public @NotNull Date getUpdated() {
    return fields.updated.getValue();
  }

  @Override
  public @Nullable Date getResolutionDate() {
    return fields.resolutiondate.getValue();
  }

  @Override
  public @Nullable Date getDueDate() {
    return fields.duedate.getValue();
  }

  @Override
  public @NotNull JiraIssueType getIssueType() {
    return fields.issuetype.getValue();
  }

  @Override
  public @Nullable JiraUser getAssignee() {
    return fields.assignee.getValue();
  }

  @Override
  public @Nullable JiraUser getReporter() {
    return fields.reporter.getValue();
  }

  @Override
  public @NotNull List<JiraComment> getComments() {
    return fields.comment.getValue();
  }

  @Override
  public @NotNull JiraStatus getStatus() {
    return fields.status.getValue();
  }

  public static class FieldWrapper<T> {
    /**
     * Serialization constructor
     */
    public FieldWrapper() {
      // empty
    }

    public FieldWrapper(T value) {
      this.value = value;
    }

    T value;

    public T getValue() {
      return value;
    }
  }

  public static class Fields {
    private FieldWrapper<JiraUser> reporter;
    private FieldWrapper<JiraUser> assignee;
    private FieldWrapper<String > summary;
    private FieldWrapper<String> description;
    private FieldWrapper<Date> created;
    private FieldWrapper<Date> updated;
    private FieldWrapper<Date> resolutiondate;
    private FieldWrapper<Date> duedate;
    private FieldWrapper<JiraStatus> status;
    private FieldWrapper<JiraIssueType> issuetype;
    private FieldWrapper<List<JiraComment>> comment;
  }
}
