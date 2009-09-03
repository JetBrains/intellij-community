/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.actions;

import com.intellij.uiDesigner.CaptionSelection;
import com.intellij.uiDesigner.GridChangeUtil;
import com.intellij.uiDesigner.UIDesignerBundle;

/**
 * @author yole
*/
public final class SplitAction extends RowColumnAction {
  public SplitAction() {
    super(UIDesignerBundle.message("action.split.column"), "/com/intellij/uiDesigner/icons/splitColumn.png",
          UIDesignerBundle.message("action.split.row"), "/com/intellij/uiDesigner/icons/splitRow.png");
  }

  protected void actionPerformed(CaptionSelection selection) {
    GridChangeUtil.splitCell(selection.getContainer(), selection.getFocusedIndex(), selection.isRow());
  }
}
