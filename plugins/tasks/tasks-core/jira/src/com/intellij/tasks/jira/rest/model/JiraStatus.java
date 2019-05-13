/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
  @NotNull
  public String getId() {
    if (id == null) {
      String[] parts = self.split("/");
      assert parts.length > 0;
      id = parts[parts.length - 1];
    }
    return id;
  }

  @NotNull
  public String getStatusUrl() {
    return self;
  }

  @NotNull
  public String getName() {
    return name;
  }
}
