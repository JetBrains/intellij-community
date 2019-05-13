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
    return fields.summary.getValue();
  }

  @Nullable
  @Override
  public String getDescription() {
    return fields.description.getValue();
  }

  @NotNull
  @Override
  public Date getCreated() {
    return fields.created.getValue();
  }

  @NotNull
  @Override
  public Date getUpdated() {
    return fields.updated.getValue();
  }

  @Nullable
  @Override
  public Date getResolutionDate() {
    return fields.resolutiondate.getValue();
  }

  @Nullable
  @Override
  public Date getDueDate() {
    return fields.duedate.getValue();
  }

  @NotNull
  @Override
  public JiraIssueType getIssueType() {
    return fields.issuetype.getValue();
  }

  @Nullable
  @Override
  public JiraUser getAssignee() {
    return fields.assignee.getValue();
  }

  @Nullable
  @Override
  public JiraUser getReporter() {
    return fields.reporter.getValue();
  }

  @NotNull
  @Override
  public List<JiraComment> getComments() {
    return fields.comment.getValue();
  }

  @NotNull
  @Override
  public JiraStatus getStatus() {
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
