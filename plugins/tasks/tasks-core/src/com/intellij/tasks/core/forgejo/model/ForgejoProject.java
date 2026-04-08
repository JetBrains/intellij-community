// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.core.forgejo.model;

import com.google.gson.annotations.SerializedName;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * Forgejo repository DTO. Fields initialized reflectively by GSON.
 * Only required fields are declared.
 * Fields except id may be null because they are not serialized.
 */
@SuppressWarnings("unused")
@Tag("ForgejoProject")
public class ForgejoProject {
  private int id;
  @SerializedName("full_name")
  private String fullName;
  private String name;
  @SerializedName("html_url")
  private String htmlUrl;

  @Override
  public final boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ForgejoProject)) return false;
    return id == ((ForgejoProject)o).id;
  }

  @Override
  public final int hashCode() {
    return id;
  }

  @Attribute("id")
  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }

  public @Nullable String getName() {
    return name;
  }

  public @Nullable String getFullName() {
    return fullName;
  }

  public void setFullName(@Nullable String fullName) {
    this.fullName = fullName;
  }

  public @Nullable String getHtmlUrl() {
    return htmlUrl;
  }

  @Override
  public final @NlsSafe String toString() {
    return getFullName() != null ? getFullName() : getName();
  }
}
