// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.palette;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public interface PaletteGroup {
  PaletteGroup[] EMPTY_ARRAY = new PaletteGroup[0];

  PaletteItem[] getItems();

  /**
   * Returns the text of the group header for the palette group.
   *
   * @return the text of the group header for the palette group, or null if no header should be shown.
   */
  @Nullable @NlsSafe String getName();

  String getTabName();

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
   */
  void uiDataSnapshot(@NotNull DataSink sink, @NotNull Project project);

  /**
   * Processes the drop of a palette item on the specified index in the palette group.
   *
   * @param project the project to which the drop target palette belongs.
   * @param item    the dropped item.
   * @param index   the index at which the dropped item should be inserted (from 0 to getItems().length).
   */
  void handleDrop(Project project, PaletteItem item, int index);
}
