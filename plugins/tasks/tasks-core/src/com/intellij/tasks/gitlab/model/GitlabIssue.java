// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.gitlab.model;

import com.google.gson.annotations.SerializedName;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;

import java.util.Date;

/**
 * This is a stub definition intended to be used with Google GSON. Its fields are initialized reflectively.
 */
@SuppressWarnings("unused")
public class GitlabIssue {

  public static class TimeStats {
    private int total_time_spent;
  }

  private int id;
  @SerializedName("iid")
  private int localId;
  private String title;
  private String description;
  @SerializedName("project_id")
  private int projectId;
  @SerializedName("updated_at")
  private Date updatedAt;
  @SerializedName("created_at")
  private Date createdAt;
  private String state;
  @SerializedName("time_stats")
  private TimeStats timeStats;


  public int getId() {
    return id;
  }

  public @NotNull @NlsSafe String getTitle() {
    return title;
  }

  public @NotNull String getDescription() {
    return description;
  }

  public int getProjectId() {
    return projectId;
  }

  public @NotNull Date getUpdatedAt() {
    return updatedAt;
  }

  public @NotNull Date getCreatedAt() {
    return createdAt;
  }

  public @NotNull String getState() {
    return state;
  }

  public int getLocalId() {
    return localId;
  }

  public int getTimeSpent() { return timeStats.total_time_spent; }
}