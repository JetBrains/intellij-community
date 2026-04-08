// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.core.forgejo.model;

import com.google.gson.annotations.SerializedName;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

@SuppressWarnings("unused")
public class ForgejoIssue {
  private int id;
  private int number;
  private String title;
  private String body;
  private String state;
  @SerializedName("html_url")
  private String htmlUrl;
  @SerializedName("updated_at")
  private Date updatedAt;
  @SerializedName("created_at")
  private Date createdAt;
  private ForgejoProject repository;

  public int getId() {
    return id;
  }

  public int getNumber() {
    return number;
  }

  public @NotNull @NlsSafe String getTitle() {
    return title;
  }

  public @Nullable String getBody() {
    return body;
  }

  public @NotNull String getState() {
    return state;
  }

  public @Nullable String getHtmlUrl() {
    return htmlUrl;
  }

  public @NotNull Date getUpdatedAt() {
    return updatedAt;
  }

  public @NotNull Date getCreatedAt() {
    return createdAt;
  }

  public @Nullable ForgejoProject getRepository() {
    return repository;
  }
}
