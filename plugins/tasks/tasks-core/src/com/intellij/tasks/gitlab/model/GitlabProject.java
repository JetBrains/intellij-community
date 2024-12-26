// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.gitlab.model;

import com.google.gson.annotations.SerializedName;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * This is a stub definition intended to be used with Google GSON. Its fields are initialized reflectively.
 * 
 * Only required fields are declared.
 * Field {@code name} and {@code webUrl} may be null because only {@code id} is serialized.
 */
@SuppressWarnings("unused")
@Tag("GitlabProject")
public class GitlabProject {
  private int id;
  private String name;
  @SerializedName("web_url")
  private String webUrl;

  @Override
  public final boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof GitlabProject)) return false;
    return id == ((GitlabProject)o).id;
  }

  @Override
  public final int hashCode() {
    return id;
  }

  @Attribute("id")
  public int getId() {
    return id;
  }

  /**
   * For serialization purposes only
   */
  public void setId(int id) {
    this.id = id;
  }

  public @Nullable String getName() {
    return name;
  }

  public @Nullable String getWebUrl() {
    return webUrl;
  }

  @Override
  public final String toString() {
    return getName();
  }
}
