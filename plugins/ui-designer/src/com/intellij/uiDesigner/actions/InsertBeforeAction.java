// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.actions;

import com.intellij.uiDesigner.CaptionSelection;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.radComponents.RadContainer;
import icons.UIDesignerIcons;

/**
 * @author yole
*/
public final class InsertBeforeAction extends RowColumnAction {
  public InsertBeforeAction() {
    super(UIDesignerBundle.message("action.insert.column.before.this"), UIDesignerIcons.InsertColumnLeft,
          UIDesignerBundle.message("action.insert.row.before.this"), UIDesignerIcons.InsertRowAbove);
  }

  @Override
  protected void actionPerformed(CaptionSelection selection) {
    RadContainer container = selection.getContainer();
    container.getGridLayoutManager().insertGridCells(container, selection.getFocusedIndex(), selection.isRow(), true, false);
  }
}
