// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.designSurface.InplaceEditingLayer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;


public abstract class AbstractGuiEditorAction extends AnAction implements DumbAware {
  private final boolean myModifying;

  protected AbstractGuiEditorAction() {
    myModifying = false;
  }

  protected AbstractGuiEditorAction(final boolean modifying) {
    myModifying = modifying;
  }

  @Override
  public final void actionPerformed(final @NotNull AnActionEvent e) {
    final GuiEditor editor = FormEditingUtil.getEditorFromContext(e.getDataContext());
    if (editor != null) {
      final ArrayList<RadComponent> selection = FormEditingUtil.getSelectedComponents(editor);
      if (myModifying) {
        if (!editor.ensureEditable()) return;
      }
      InplaceEditingLayer editingLayer = editor.getInplaceEditingLayer();
      if (editingLayer.isEditing()) {
        editingLayer.finishInplaceEditing();
      }
      Runnable runnable = () -> {
        actionPerformed(editor, selection, e);
        if (myModifying) {
          editor.refreshAndSave(true);
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

  protected abstract void actionPerformed(final GuiEditor editor, final List<? extends RadComponent> selection, final AnActionEvent e);

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public final void update(@NotNull AnActionEvent e) {
    GuiEditor editor = FormEditingUtil.getEditorFromContext(e.getDataContext());
    if (editor == null) {
      e.getPresentation().setEnabledAndVisible(false);
    }
    else {
      e.getPresentation().setEnabledAndVisible(true);
      final ArrayList<RadComponent> selection = FormEditingUtil.getSelectedComponents(editor);
      update(editor, selection, e);
    }
  }

  protected void update(@NotNull GuiEditor editor, final ArrayList<? extends RadComponent> selection, final AnActionEvent e) {
  }

  protected @Nullable
  @Nls String getCommandName() {
    return null;
  }
}
