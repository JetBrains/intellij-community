// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.tasks.trello.model;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;

/**
 * This is a stub definition intended to be used with Google GSON. Its fields are initialized reflectively.
 */
@Tag("TrelloUser")
public class TrelloUser extends TrelloModel {

  public static final String REQUIRED_FIELDS = "username";

  private @NotNull String username = "";

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

  @Attribute("name")
  @Override
  public @NotNull String getName() {
    return getUsername();
  }

  @Override
  public void setName(@NotNull String name) {
    username = name;
  }

  public @NotNull String getUsername() {
    return username;
  }
}
