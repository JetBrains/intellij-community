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

  @NotNull
  @Override
  public String getKey() {
    return key;
  }

  @NotNull
  @Override
  public String getIssueUrl() {
    return self;
  }

  @NotNull
  @Override
  public String getSummary() {
    return fields.summary;
  }

  @Nullable
  @Override
  public String getDescription() {
    return fields.description;
  }

  @NotNull
  @Override
  public Date getCreated() {
    return fields.created;
  }

  @NotNull
  @Override
  public Date getUpdated() {
    return fields.updated;
  }

  @Nullable
  @Override
  public Date getResolutionDate() {
    return fields.resolutiondate;
  }

  @Nullable
  @Override
  public Date getDueDate() {
    return fields.duedate;
  }

  @NotNull
  @Override
  public JiraIssueType getIssueType() {
    return fields.issuetype;
  }

  @Nullable
  @Override
  public JiraUser getAssignee() {
    return fields.assignee;
  }

  @Nullable
  @Override
  public JiraUser getReporter() {
    return fields.reporter;
  }

  @NotNull
  @Override
  public List<JiraComment> getComments() {
    return fields.comment == null ? ContainerUtil.<JiraComment>emptyList() : fields.comment.getComments();
  }

  @NotNull
  @Override
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
