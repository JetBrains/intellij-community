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

package com.intellij.tasks.trello.model;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Mikhail Golubev
 */

@Tag("TrelloUser")
public class TrelloUser extends TrelloModel {

  private String username, fullName, initials, email;
  private List<String> idBoards, idOrganizations;
  private String url;

  /**
   * Serialization constructor
   */
  @SuppressWarnings("UnusedDeclaration")
  public TrelloUser() {
  }

  @Override
  public String toString() {
    return String.format("TrelloUser(id='%s' username='%s')", getId(), username);
  }

  @NotNull
  @Attribute("name")
  @Override
  public String getName() {
    return getUsername();
  }

  @Override
  public void setName(@NotNull String name) {
    username = name;
  }

  @NotNull
  public String getUsername() {
    return username;
  }

  @NotNull
  public String getFullName() {
    return fullName;
  }

  @NotNull
  public String getInitials() {
    return initials;
  }

  @Nullable
  public String getEmail() {
    return email;
  }

  @NotNull
  public List<String> getIdBoards() {
    return idBoards;
  }

  @NotNull
  public List<String> getIdOrganizations() {
    return idOrganizations;
  }

  @NotNull
  public String getUrl() {
    return url;
  }
}
