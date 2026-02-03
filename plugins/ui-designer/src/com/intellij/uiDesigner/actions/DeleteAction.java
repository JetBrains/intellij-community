// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.uiDesigner.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.uiDesigner.CaptionSelection;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import org.jetbrains.annotations.NotNull;

public final class DeleteAction extends AnAction {
  public DeleteAction() {
    getTemplatePresentation().setIcon(AllIcons.General.Remove);
  }

  @Override
  public void actionPerformed(final @NotNull AnActionEvent e) {
    final GuiEditor editor = FormEditingUtil.getEditorFromContext(e.getDataContext());
    CaptionSelection selection = e.getData(CaptionSelection.DATA_KEY);
    if (editor == null || selection == null || selection.getFocusedIndex() < 0) return;
    FormEditingUtil.deleteRowOrColumn(editor, selection.getContainer(), selection.getSelection(), selection.isRow());
    selection.getContainer().revalidate();
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void update(final @NotNull AnActionEvent e) {
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
