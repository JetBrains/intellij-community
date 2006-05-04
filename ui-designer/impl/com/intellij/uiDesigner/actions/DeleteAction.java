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
    CaptionSelection selection = (CaptionSelection) e.getDataContext().getData(CaptionSelection.class.getName());
    if (editor == null || selection == null) return;
    FormEditingUtil.deleteRowOrColumn(editor, selection.getContainer(), selection.getFocusedIndex(), selection.isRow());
  }

  public void update(final AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    CaptionSelection selection = (CaptionSelection) e.getDataContext().getData(CaptionSelection.class.getName());
    if(selection == null || selection.getContainer() == null){
      presentation.setVisible(false);
      return;
    }
    presentation.setVisible(true);
    presentation.setText(!selection.isRow()
                         ? UIDesignerBundle.message("action.delete.column")
                         : UIDesignerBundle.message("action.delete.row"));

    if(selection.isRow() && selection.getContainer().getGridRowCount() < 2) {
      presentation.setEnabled(false);
    }
    else if (!selection.isRow() && selection.getContainer().getGridColumnCount() < 2) {
      presentation.setEnabled(false);
    }
    else {
      presentation.setEnabled(true);
    }
  }
}
