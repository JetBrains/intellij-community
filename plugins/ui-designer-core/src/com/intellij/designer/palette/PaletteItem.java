/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.designer.palette;

import com.intellij.designer.model.MetaModel;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Alexander Lobas
 */
public interface PaletteItem {
  String getTitle();

  Icon getIcon();

  String getTooltip();

  String getVersion();

  boolean isEnabled();

  /**
   * Returns null or empty if this item is not deprecated, and otherwise returns the version
   * the item was deprecated in.
   *
   * @return null or empty if the item is not deprecated, otherwise a version
   */
  @Nullable
  String getDeprecatedIn();

  /**
   * Returns a hint regarding the deprecation. Can be null or empty.
   *
   * @return a hint describing the deprecated item.
   */
  @Nullable
  String getDeprecatedHint();

  /**
   * @return the creation data to be used by {@link com.intellij.designer.model.MetaModel#getCreation()}
   */
  String getCreation();

  /**
   * Returns the associated {@link com.intellij.designer.model.MetaModel}, if known
   */
  MetaModel getMetaModel();

  /**
   * Sets the associated {@link com.intellij.designer.model.MetaModel}, if known
   */
  void setMetaModel(MetaModel metaModel);
}