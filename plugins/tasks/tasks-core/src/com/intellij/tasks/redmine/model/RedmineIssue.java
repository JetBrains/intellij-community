package com.intellij.tasks.redmine.model;

import com.google.gson.annotations.SerializedName;
import com.intellij.tasks.impl.gson.Mandatory;
import com.intellij.tasks.impl.gson.RestModel;
import org.jetbrains.annotations.NotNull;

import java.util.Date;

/**
 * @author Mikhail Golubev
 */
@RestModel
@SuppressWarnings("UnusedDeclaration")
public class RedmineIssue {
  private int id;
  @Mandatory
  private IssueStatus status;
  @Mandatory
  private String subject;
  @Mandatory
  private String description;
  @SerializedName("done_ratio")
  private int doneRatio;
  @Mandatory
  @SerializedName("created_on")
  private Date created;
  @Mandatory
  @SerializedName("updated_on")
  private Date updated;

  public int getId() {
    return id;
  }

  public IssueStatus getStatus() {
    return status;
  }

  @NotNull
  public String getSubject() {
    return subject;
  }

  @NotNull
  public String getDescription() {
    return description;
  }

  public int getDoneRatio() {
    return doneRatio;
  }

  @NotNull
  public Date getCreated() {
    return created;
  }

  @NotNull
  public Date getUpdated() {
    return updated;
  }

  @RestModel
  public static class IssueStatus {
    private int id;
    @Mandatory
    private String name;

    public int getId() {
      return id;
    }

    @NotNull
    public String getName() {
      return name;
    }
  }
}
