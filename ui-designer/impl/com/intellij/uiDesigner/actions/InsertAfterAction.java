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
public final class InsertAfterAction extends RowColumnAction {
  public InsertAfterAction() {
    super(UIDesignerBundle.message("action.insert.column.after.this"), "/com/intellij/uiDesigner/icons/insertColumnRight.png",
          UIDesignerBundle.message("action.insert.row.after.this"), "/com/intellij/uiDesigner/icons/insertRowBelow.png");
  }

  protected void actionPerformed(final CaptionSelection selection) {
    final RadContainer container = selection.getContainer();
    container.getLayoutManager().insertGridCells(container, selection.getFocusedIndex(), selection.isRow(), false);
  }
}
