/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  public final void actionPerformed(final AnActionEvent e) {
    final GuiEditor editor = FormEditingUtil.getEditorFromContext(e.getDataContext());
    if (editor != null) {
      final ArrayList<RadComponent> selection = FormEditingUtil.getSelectedComponents(editor);
      if (myModifying) {
        if (!editor.ensureEditable()) return;
      }
      Runnable runnable = new Runnable() {
        public void run() {
          actionPerformed(editor, selection, e);
          if (myModifying) {
            editor.refreshAndSave(true);
          }
        }
      };
      if (getCommandName() != null) {
        CommandProcessor.getInstance().executeCommand(editor.getProject(), runnable, getCommandName(), null);
      }
      else {
        runnable.run();
      }
    }
  }

  protected abstract void actionPerformed(final GuiEditor editor, final List<RadComponent> selection, final AnActionEvent e);

  public final void update(AnActionEvent e) {
    GuiEditor editor = FormEditingUtil.getEditorFromContext(e.getDataContext());
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

  @Nullable
  protected String getCommandName() {
    return null;
  }
}
