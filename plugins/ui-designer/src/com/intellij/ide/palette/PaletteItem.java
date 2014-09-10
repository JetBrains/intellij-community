/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.ide.palette;

import com.intellij.ide.dnd.DnDDragStartBean;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ColoredListCellRenderer;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PaletteItem {
  void customizeCellRenderer(ColoredListCellRenderer cellRenderer,
                             boolean selected,
                             boolean hasFocus);

  /**
   * Processes dragging the item.
   *
   * @return the drag start bean for the drag process, or null if the item cannot be dragged.
   */
  @Nullable DnDDragStartBean startDragging();

  /**
   * Returns the action group from which the context menu is built when the palette
   * item is right-clicked.
   *
   * @return the action group, or null if no context menu should be shown.
   */
  @Nullable ActionGroup getPopupActionGroup();

  /**
   * Returns the data for the specified data constant.
   *
   * @param project the project in the context of which data is requested.
   * @param dataId  the data constant id (see {@link com.intellij.openapi.actionSystem.PlatformDataKeys}).
   * @return the data item, or null if no data is available for this constant.
   */
  @Nullable Object getData(Project project, String dataId);
}
