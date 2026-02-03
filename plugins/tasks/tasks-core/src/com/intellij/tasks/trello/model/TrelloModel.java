// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.tasks.trello.model;

import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;

public abstract class TrelloModel {
  public static final String ILLEGAL_ID = "ILLEGAL_ID";

  private String id = ILLEGAL_ID;


  /**
   * Trello always provides objects IDs as part of its REST responses. But it may still be null
   * if e.g. object was incorrectly created manually, or it is some kind of stub implementation
   * as for UNSPECIFIED_BOARD and UNSPECIFIED_LIST in TrelloRepositoryEditor. ILLEGAL_ID
   * value serves as fallback in such cases.
   */
  @Attribute("id")
  public @NotNull String getId() {
    return id;
  }

  /**
   * For serialization purposes only
   */
  public void setId(@NotNull String id) {
    this.id = id;
  }

  /**
   * Every model has human-readable name
   */
  public abstract @NotNull String getName();


  /**
   * Only for serialization
   */
  public abstract void setName(@NotNull String name);

  /**
   * Object can be compared by their unique id.
   */
  @Override
  public final boolean equals(Object obj) {
    if (obj == this) return true;
    if (!(obj instanceof TrelloModel model)) return false;
    return !id.equals(ILLEGAL_ID) && id.equals(model.id);
  }

  @Override
  public final int hashCode() {
    return id.hashCode();
  }
}
