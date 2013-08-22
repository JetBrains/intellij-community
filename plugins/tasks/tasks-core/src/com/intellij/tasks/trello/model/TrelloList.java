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

/**
 * @author Mikhail Golubev
 */
@Tag("TrelloList")
public class TrelloList extends TrelloModel {
  private boolean closed;
  private String idBoard;
  private String name;
  private double pos;
  /**
   * This field is not part of REST responses. It will be set explicitly to show in UI, that
   * selected list doesn't belong to specified board anymore.
   *
   * @see com.intellij.tasks.trello.TrelloRepositoryEditor
   */
  private boolean moved;

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

  @NotNull
  public String getIdBoard() {
    return idBoard;
  }

  @NotNull
  @Attribute("name")
  @Override
  public String getName() {
    return name;
  }

  @Override
  public void setName(@NotNull String name) {
    this.name = name;
  }

  public double getPos() {
    return pos;
  }

  public boolean isMoved() {
    return moved;
  }

  public void setMoved(boolean moved) {
    this.moved = moved;
  }
}
