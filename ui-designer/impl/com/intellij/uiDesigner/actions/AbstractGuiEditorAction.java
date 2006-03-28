/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.GuiEditorUtil;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public abstract class AbstractGuiEditorAction extends AnAction {
  private boolean myModifying;

  protected AbstractGuiEditorAction() {
    myModifying = false;
  }

  protected AbstractGuiEditorAction(final boolean modifying) {
    myModifying = modifying;
  }

  public final void actionPerformed(AnActionEvent e) {
    GuiEditor editor = GuiEditorUtil.getEditorFromContext(e.getDataContext());
    if (editor != null) {
      final ArrayList<RadComponent> selection = FormEditingUtil.getSelectedComponents(editor);
      if (myModifying) {
        if (!editor.ensureEditable()) return;
      }
      actionPerformed(editor, selection, e);
      if (myModifying) {
        editor.refreshAndSave(true);
      }
    }
  }

  protected abstract void actionPerformed(final GuiEditor editor, final List<RadComponent> selection, final AnActionEvent e);

  public final void update(AnActionEvent e) {
    GuiEditor editor = GuiEditorUtil.getEditorFromContext(e.getDataContext());
    if (editor == null) {
      e.getPresentation().setVisible(false);
      e.getPresentation().setEnabled(false);
    }
    else {
      e.getPresentation().setVisible(true);
      e.getPresentation().setEnabled(true);
      final ArrayList<RadComponent> selection = FormEditingUtil.getSelectedComponents(editor);
      update(editor, selection, e);
    }
  }

  protected void update(@NotNull GuiEditor editor, final ArrayList<RadComponent> selection, final AnActionEvent e) {
  }
}
