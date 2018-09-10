// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.uiDesigner.CaptionSelection;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import icons.UIDesignerIcons;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
*/
public final class DeleteAction extends AnAction {
  public DeleteAction() {
    getTemplatePresentation().setIcon(UIDesignerIcons.DeleteCell);
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    final GuiEditor editor = FormEditingUtil.getEditorFromContext(e.getDataContext());
    CaptionSelection selection = CaptionSelection.DATA_KEY.getData(e.getDataContext());
    if (editor == null || selection == null || selection.getFocusedIndex() < 0) return;
    FormEditingUtil.deleteRowOrColumn(editor, selection.getContainer(), selection.getSelection(), selection.isRow());
    selection.getContainer().revalidate();
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    CaptionSelection selection = CaptionSelection.DATA_KEY.getData(e.getDataContext());
    if(selection == null || selection.getContainer() == null){
      presentation.setVisible(false);
      return;
    }
    presentation.setVisible(true);
    if (selection.getSelection().length > 1) {
      presentation.setText(!selection.isRow()
                           ? UIDesignerBundle.message("action.delete.columns")
                           : UIDesignerBundle.message("action.delete.rows"));
    }
    else {
      presentation.setText(!selection.isRow()
                           ? UIDesignerBundle.message("action.delete.column")
                           : UIDesignerBundle.message("action.delete.row"));
    }

    int minCellCount = selection.getContainer().getGridLayoutManager().getMinCellCount();
    if (selection.getContainer().getGridCellCount(selection.isRow()) - selection.getSelection().length < minCellCount) {
      presentation.setEnabled(false);
    }
    else if (selection.getFocusedIndex() < 0) {
      presentation.setEnabled(false);
    }
    else {
      presentation.setEnabled(true);
    }
  }
}
