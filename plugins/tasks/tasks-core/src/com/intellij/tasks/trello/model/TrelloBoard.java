// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.tasks.trello.model;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This is a stub definition intended to be used with Google GSON. Its fields are initialized reflectively.
 */
@SuppressWarnings("UnusedDeclaration")
@Tag("TrelloBoard")
public class TrelloBoard extends TrelloModel {

  public static final String REQUIRED_FIELDS = "closed,name,idOrganization";

  private boolean closed;
  private String idOrganization;
  private @NotNull String name = "";

  /**
   * Serialization constructor
   */
  @SuppressWarnings("UnusedDeclaration")
  public TrelloBoard() {
  }

  @Override
  public String toString() {
    return String.format("TrelloBoard(id='%s', name='%s')", getId(), getName());
  }

  public boolean isClosed() {
    return closed;
  }

  public @Nullable String getIdOrganization() {
    return idOrganization;
  }

  @Attribute("name")
  @Override
  public @NotNull @NlsSafe String getName() {
    return name;
  }

  @Override
  public void setName(@NotNull String name) {
    this.name = name;
  }

}
