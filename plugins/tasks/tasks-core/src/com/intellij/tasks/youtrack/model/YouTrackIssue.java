// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.youtrack.model;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @noinspection unused
 */
public class YouTrackIssue {
  public static final String DEFAULT_FIELDS = "idReadable,updated,created,resolved,summary,description,customFields(name,value(name))";

  @SerializedName("idReadable")
  private String id;
  private String summary;
  private String description;
  private long created;
  private long updated;
  private long resolved;
  private List<CustomField> customFields;

  @NotNull
  public @NlsSafe String getId() {
    return id;
  }

  @NotNull
  public @NlsSafe String getSummary() {
    return summary;
  }

  @NotNull
  public @NlsSafe String getDescription() {
    return StringUtil.notNullize(description);
  }

  public long getCreated() {
    return created;
  }

  public long getUpdated() {
    return updated;
  }

  public long getResolved() {
    return resolved;
  }

  @NotNull
  public List<CustomField> getCustomFields() {
    return ContainerUtil.notNullize(customFields);
  }

  public static class CustomField {
    private String name;
    private JsonElement value;

    @NotNull
    public String getName() {
      return name;
    }

    @Nullable
    public JsonElement getValue() {
      return value;
    }
  }
}
