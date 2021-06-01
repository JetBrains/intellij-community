// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.palette;

import com.intellij.ide.dnd.DnDDragStartBean;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ColoredListCellRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


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
  @Nullable Object getData(Project project, @NotNull String dataId);
}
