/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.FormEditingUtil;

/**
 * @author yole
 */
public class ShowComponentTagsAction extends ToggleAction {
  public void update(final AnActionEvent e) {
    super.update(e);
    GuiEditor editor = FormEditingUtil.getEditorFromContext(e.getDataContext());
    e.getPresentation().setEnabled(editor != null);
  }

  public boolean isSelected(AnActionEvent e) {
    GuiEditor editor = FormEditingUtil.getEditorFromContext(e.getDataContext());
    return editor != null && editor.isShowComponentTags();
  }

  public void setSelected(AnActionEvent e, boolean state) {
    GuiEditor editor = FormEditingUtil.getEditorFromContext(e.getDataContext());
    if (editor != null) {
      editor.setShowComponentTags(state);
    }
  }
}
