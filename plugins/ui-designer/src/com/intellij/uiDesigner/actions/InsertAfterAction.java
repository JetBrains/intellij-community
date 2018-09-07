// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.actions;

import com.intellij.uiDesigner.CaptionSelection;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.radComponents.RadContainer;
import icons.UIDesignerIcons;

/**
 * @author yole
*/
public final class InsertAfterAction extends RowColumnAction {
  public InsertAfterAction() {
    super(UIDesignerBundle.message("action.insert.column.after.this"), UIDesignerIcons.InsertColumnRight,
          UIDesignerBundle.message("action.insert.row.after.this"), UIDesignerIcons.InsertRowBelow);
  }

  @Override
  protected void actionPerformed(final CaptionSelection selection) {
    final RadContainer container = selection.getContainer();
    container.getGridLayoutManager().insertGridCells(container, selection.getFocusedIndex(), selection.isRow(), false, false);
  }
}
