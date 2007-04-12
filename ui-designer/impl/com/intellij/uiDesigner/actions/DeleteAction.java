/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.util.IconLoader;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.CaptionSelection;
import com.intellij.uiDesigner.UIDesignerBundle;

/**
 * @author yole
*/
public final class DeleteAction extends AnAction {
  public DeleteAction() {
    getTemplatePresentation().setIcon(IconLoader.getIcon("/com/intellij/uiDesigner/icons/deleteCell.png"));
  }

  public void actionPerformed(final AnActionEvent e) {
    final GuiEditor editor = FormEditingUtil.getEditorFromContext(e.getDataContext());
    CaptionSelection selection = e.getData(CaptionSelection.DATA_KEY);
    if (editor == null || selection == null || selection.getFocusedIndex() < 0) return;
    FormEditingUtil.deleteRowOrColumn(editor, selection.getContainer(), selection.getSelection(), selection.isRow());
    selection.getContainer().revalidate();
  }

  public void update(final AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    CaptionSelection selection = e.getData(CaptionSelection.DATA_KEY);
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
