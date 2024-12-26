// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.tasks.trello.model;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;

/**
 * This is a stub definition intended to be used with Google GSON. Its fields are initialized reflectively.
 */
@SuppressWarnings("UnusedDeclaration")
@Tag("TrelloList")
public class TrelloList extends TrelloModel {

  public static final String REQUIRED_FIELDS = "closed,name,idBoard";

  private boolean closed;
  private String idBoard;
  private @NotNull String name = "";
  /**
   * This field is not part of REST responses. It will be set explicitly to show in UI, that
   * selected list doesn't belong to specified board anymore.
   *
   * @see com.intellij.tasks.trello.TrelloRepositoryEditor
   */
  private boolean myMoved;

  /**
   * Serialization constructor
   */
  @SuppressWarnings("UnusedDeclaration")
  public TrelloList() {
  }

  @Override
  public String toString() {
    return String.format("TrelloList(id='%s' name='%s')", getId(), getName());
  }

  public boolean isClosed() {
    return closed;
  }

  public @NotNull String getIdBoard() {
    return idBoard;
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

  @Transient
  public boolean isMoved() {
    return myMoved;
  }

  public void setMoved(boolean moved) {
    this.myMoved = moved;
  }
}
