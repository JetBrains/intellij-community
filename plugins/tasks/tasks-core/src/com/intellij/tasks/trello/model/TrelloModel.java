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
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
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
  @NotNull
  public String getId() {
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
  @NotNull
  public abstract String getName();


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
    if (!(obj instanceof TrelloModel)) return false;
    TrelloModel model = (TrelloModel) obj;
    return !id.equals(ILLEGAL_ID) && id.equals(model.id);
  }

  @Override
  public final int hashCode() {
    return id.hashCode();
  }
}
