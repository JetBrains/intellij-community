package com.intellij.tasks.redmine.model;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.io.mandatory.Mandatory;
import org.jetbrains.io.mandatory.RestModel;

import java.util.List;

/**
 * This is a stub definition intended to be used with Google GSON. Its fields are initialized reflectively.
 */
@SuppressWarnings("UnusedDeclaration")
public abstract class RedmineResponseWrapper {

  private int offset;
  private int limit;
  @SerializedName("total_count")
  private int totalCount;

  public int getOffset() {
    return offset;
  }

  public int getLimit() {
    return limit;
  }

  public int getTotalCount() {
    return totalCount;
  }


  @RestModel
  public static class IssuesWrapper extends RedmineResponseWrapper {
    @Mandatory
    private List<RedmineIssue> issues;

    @NotNull
    public List<RedmineIssue> getIssues() {
      return issues;
    }
  }

  @RestModel
  public static class IssueWrapper extends RedmineResponseWrapper {
    @Mandatory
    private RedmineIssue issue;

    @NotNull
    public RedmineIssue getIssue() {
      return issue;
    }
  }

  @RestModel
  public static class ProjectsWrapper extends RedmineResponseWrapper {
    @Mandatory
    private List<RedmineProject> projects;

    @NotNull
    public List<RedmineProject> getProjects() {
      return projects;
    }
  }
}
