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
public final class InsertBeforeAction extends AnAction {
  public void actionPerformed(final AnActionEvent e) {
    GuiEditor editor = FormEditingUtil.getEditorFromContext(e.getDataContext());
    CaptionSelection selection = (CaptionSelection) e.getDataContext().getData(CaptionSelection.class.getName());
    if (editor == null || selection == null || !editor.ensureEditable()) {
      return;
    }
    selection.getContainer().getLayoutManager().insertGridCells(selection.getContainer(), selection.getFocusedIndex(),
                                                                selection.isRow(), true);
    editor.refreshAndSave(true);
  }

  public void update(final AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    CaptionSelection selection = (CaptionSelection) e.getDataContext().getData(CaptionSelection.class.getName());
    if (selection == null) {
      presentation.setEnabled(false);
    }
    else {
      presentation.setEnabled(selection.getContainer() != null);
      if (!selection.isRow()) {
        presentation.setText(UIDesignerBundle.message("action.insert.column.before.this"));
        presentation.setIcon(IconLoader.getIcon("/com/intellij/uiDesigner/icons/insertColumnLeft.png"));
      }
      else {
        presentation.setText(UIDesignerBundle.message("action.insert.row.before.this"));
        presentation.setIcon(IconLoader.getIcon("/com/intellij/uiDesigner/icons/insertRowAbove.png"));
      }
    }
  }
}
