// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class ShowGridAction extends ToggleAction {
  @Override
  public void update(@NotNull final AnActionEvent e) {
    super.update(e);
    GuiEditor editor = FormEditingUtil.getEditorFromContext(e.getDataContext());
    e.getPresentation().setEnabled(editor != null);
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    GuiEditor editor = FormEditingUtil.getEditorFromContext(e.getDataContext());
    return editor != null && editor.isShowGrid();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    GuiEditor editor = FormEditingUtil.getEditorFromContext(e.getDataContext());
    if (editor != null) {
      editor.setShowGrid(state);
    }
  }
}
