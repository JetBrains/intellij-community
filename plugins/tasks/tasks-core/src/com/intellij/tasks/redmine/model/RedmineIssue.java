// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.redmine.model;

import com.google.gson.annotations.SerializedName;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.mandatory.Mandatory;
import org.jetbrains.io.mandatory.RestModel;

import java.util.Date;

/**
 * This is a stub definition intended to be used with Google GSON. Its fields are initialized reflectively.
 */
@RestModel
@SuppressWarnings("UnusedDeclaration")
public class RedmineIssue {
  private int id;
  @Mandatory
  private IssueStatus status;
  @Mandatory
  private String subject;
  // IDEA-126470 May be missing if issue was not created via web-interface
  private String description;
  @SerializedName("done_ratio")
  private int doneRatio;
  @Mandatory
  @SerializedName("created_on")
  private Date created;
  @Mandatory
  @SerializedName("updated_on")
  private Date updated;

  private RedmineProject project;

  public int getId() {
    return id;
  }

  public IssueStatus getStatus() {
    return status;
  }

  public @NotNull @NlsSafe String getSubject() {
    return subject;
  }

  public @Nullable @NlsSafe String getDescription() {
    return description;
  }

  public int getDoneRatio() {
    return doneRatio;
  }

  public @NotNull Date getCreated() {
    return created;
  }

  public @NotNull Date getUpdated() {
    return updated;
  }

  public @Nullable RedmineProject getProject() {
    return project;
  }

  @RestModel
  public static class IssueStatus {
    private int id;
    @Mandatory
    private String name;

    public int getId() {
      return id;
    }

    public @NotNull String getName() {
      return name;
    }
  }
}
