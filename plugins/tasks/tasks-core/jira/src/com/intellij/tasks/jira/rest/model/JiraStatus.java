// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.jira.rest.model;

import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class JiraStatus {
  private String id;
  private String self;
  private String name;
  private String description;

  @Override
  public String toString() {
    return String.format("JiraStatus(name='%s')", name);
  }

  /**
   * Status id is necessary to determine issue status regardless of the language
   * used in JIRA installation. However it omitted in case of REST API version 2.0.alpha1.
   * Anyway it still may be extracted from status URL which is always available.
   */
  public @NotNull String getId() {
    if (id == null) {
      String[] parts = self.split("/");
      assert parts.length > 0;
      id = parts[parts.length - 1];
    }
    return id;
  }

  public @NotNull String getStatusUrl() {
    return self;
  }

  public @NotNull String getName() {
    return name;
  }
}
