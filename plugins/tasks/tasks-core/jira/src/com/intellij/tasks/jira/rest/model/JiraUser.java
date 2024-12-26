// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.jira.rest.model;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class JiraUser {
  private String name, displayName;
  private String self;

  @Override
  public String toString() {
    return String.format("JiraUser(name='%s')", name);
  }

  public @NotNull String getName() {
    return name;
  }

  public @NotNull @NlsSafe String getDisplayName() {
    return displayName;
  }

  public @NotNull String getUserUrl() {
    return self;
  }
}
