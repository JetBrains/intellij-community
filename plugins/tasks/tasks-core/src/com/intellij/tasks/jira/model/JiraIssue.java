/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.tasks.jira.model;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class JiraIssue {
  /**
   * JIRA by default will return enormous amount of fields for every task.
   * "fields" query parameter may be used for filtering however
   */
  public static final String REQUIRED_RESPONSE_FIELDS = "id,key,summary,description," +
                                                         "created,updated,duedate,resolutiondate" +
                                                         "assignee,reporter,issuetype,comment,status";

  private String id;
  private String key;
  private String self;
  private Fields fields;

  @Override
  public String toString() {
    return String.format("JiraIssue(id=%s, summary=%s)", id, fields.summary);
  }

  @NotNull
  public String getId() {
    return id;
  }

  @NotNull
  public String getKey() {
    return key;
  }

  @NotNull
  public String getIssueUrl() {
    return self;
  }

  @NotNull
  public String getSummary() {
    return fields.summary;
  }

  @NotNull
  public String getDescription() {
    return fields.description;
  }

  @NotNull
  public Date getCreated() {
    return fields.created;
  }

  @NotNull
  public Date getUpdated() {
    return fields.updated;
  }

  @Nullable
  public Date getResolutionDate() {
    return fields.resolutiondate;
  }

  @Nullable
  public Date getDueDate() {
    return fields.duedate;
  }

  @NotNull
  public JiraIssueType getIssueType() {
    return fields.issuetype;
  }

  @Nullable
  public JiraUser getAssignee() {
    return fields.assignee;
  }

  @Nullable
  public JiraUser getReporter() {
    return fields.reporter;
  }

  @NotNull
  public List<JiraComment> getComments() {
    return fields.comment == null ? ContainerUtil.<JiraComment>emptyList() : fields.comment.getComments();
  }

  public JiraStatus getStatus() {
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
