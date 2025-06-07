// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.jira.rest.model;

import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public class JiraIssueType {

  private String self;
  private String name;
  private String description;
  private String iconUrl;

  @Override
  public String toString() {
    return String.format("JiraIssueType(name='%s')", name);
  }

  public String getIssueTypeUrl() {
    return self;
  }

  public String getName() {
    return name;
  }

  /**
   * Will be available in JIRA > 5.x.x and omitted in earlier releases
   * due to REST API differences.
   */
  public @Nullable String getIconUrl() {
    return iconUrl;
  }

  /**
   * Will be available in JIRA > 5.x.x and omitted in earlier releases
   * due to REST API differences.
   */
  public @Nullable String getDescription() {
    return description;
  }
}
