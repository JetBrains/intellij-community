// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.actions;

import com.intellij.uiDesigner.CaptionSelection;
import com.intellij.uiDesigner.GridChangeUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import icons.UIDesignerIcons;

/**
 * @author yole
*/
public final class SplitAction extends RowColumnAction {
  public SplitAction() {
    super(UIDesignerBundle.message("action.split.column"), UIDesignerIcons.SplitColumn,
          UIDesignerBundle.message("action.split.row"), UIDesignerIcons.SplitRow);
  }

  @Override
  protected void actionPerformed(CaptionSelection selection) {
    GridChangeUtil.splitCell(selection.getContainer(), selection.getFocusedIndex(), selection.isRow());
  }
}
