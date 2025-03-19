// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.jira.rest.api2.model;

import com.intellij.tasks.jira.rest.model.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class JiraIssueApi2 extends JiraIssue {
  /**
   * JIRA by default will return enormous amount of fields for every task.
   * "fields" query parameter may be used for filtering however
   */
  public static final String REQUIRED_RESPONSE_FIELDS = "id,key,summary,description," +
                                                         "created,updated,duedate,resolutiondate," +
                                                         "assignee,reporter,issuetype,comment,status";

  private String id;
  private String key;
  private String self;
  private Fields fields;

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
    return fields.summary;
  }

  @Override
  public @Nullable String getDescription() {
    return fields.description;
  }

  @Override
  public @NotNull Date getCreated() {
    return fields.created;
  }

  @Override
  public @NotNull Date getUpdated() {
    return fields.updated;
  }

  @Override
  public @Nullable Date getResolutionDate() {
    return fields.resolutiondate;
  }

  @Override
  public @Nullable Date getDueDate() {
    return fields.duedate;
  }

  @Override
  public @NotNull JiraIssueType getIssueType() {
    return fields.issuetype;
  }

  @Override
  public @Nullable JiraUser getAssignee() {
    return fields.assignee;
  }

  @Override
  public @Nullable JiraUser getReporter() {
    return fields.reporter;
  }

  @Override
  public @NotNull List<JiraComment> getComments() {
    return fields.comment == null ? ContainerUtil.emptyList() : fields.comment.getComments();
  }

  @Override
  public @NotNull JiraStatus getStatus() {
    return fields.status;
  }

  public static class Fields {
    private String summary;
    private String description;
    private Date created;
    private Date updated;
    private Date resolutiondate;
    private Date duedate;
    private JiraResponseWrapper.Comments comment;

    private JiraUser assignee;
    private JiraUser reporter;

    private JiraIssueType issuetype;
    private JiraStatus status;
  }
}
