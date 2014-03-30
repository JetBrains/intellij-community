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
public class JiraUser {
  private String name, displayName;
  private String self;

  @Override
  public String toString() {
    return String.format("JiraUser(name='%s')", name);
  }

  @NotNull
  public String getName() {
    return name;
  }

  @NotNull
  public String getDisplayName() {
    return displayName;
  }

  @NotNull
  public String getUserUrl() {
    return self;
  }
}
