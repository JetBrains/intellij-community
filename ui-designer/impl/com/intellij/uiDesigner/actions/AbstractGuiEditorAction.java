/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.GuiEditorUtil;
import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * @author yole
 */
public abstract class AbstractGuiEditorAction extends AnAction {
  public final void actionPerformed(AnActionEvent e) {
    GuiEditor editor = GuiEditorUtil.getEditorFromContext(e.getDataContext());
    assert editor != null;
    final ArrayList<RadComponent> selection = FormEditingUtil.getSelectedComponents(editor);
    actionPerformed(editor, selection, e);
  }

  protected abstract void actionPerformed(final GuiEditor editor, final ArrayList<RadComponent> selection, final AnActionEvent e);

  public final void update(AnActionEvent e) {
    GuiEditor editor = GuiEditorUtil.getEditorFromContext(e.getDataContext());
    if (editor == null) {
      e.getPresentation().setVisible(false);
    }
    else {
      final ArrayList<RadComponent> selection = FormEditingUtil.getSelectedComponents(editor);
      update(editor, selection, e);
    }
  }

  protected void update(@NotNull GuiEditor editor, final ArrayList<RadComponent> selection, final AnActionEvent e) {
  }
}
