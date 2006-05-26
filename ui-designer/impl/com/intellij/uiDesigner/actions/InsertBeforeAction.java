/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.actions;

import com.intellij.uiDesigner.CaptionSelection;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.radComponents.RadContainer;

/**
 * @author yole
*/
public final class InsertBeforeAction extends RowColumnAction {
  public InsertBeforeAction() {
    super(UIDesignerBundle.message("action.insert.column.before.this"), "/com/intellij/uiDesigner/icons/insertColumnLeft.png",
          UIDesignerBundle.message("action.insert.row.before.this"), "/com/intellij/uiDesigner/icons/insertRowAbove.png");
  }

  protected void actionPerformed(CaptionSelection selection) {
    RadContainer container = selection.getContainer();
    container.getGridLayoutManager().insertGridCells(container, selection.getFocusedIndex(), selection.isRow(), true, false);
  }
}
