// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.SelectionState;
import com.intellij.uiDesigner.componentTree.ComponentPtr;
import com.intellij.uiDesigner.componentTree.ComponentTreeBuilder;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.propertyInspector.DesignerToolWindowManager;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;

public final class ShrinkSelectionAction extends AnAction{
  @Override
  public void actionPerformed(final @NotNull AnActionEvent e) {
    final GuiEditor editor = FormEditingUtil.getEditorFromContext(e.getDataContext());
    assert editor != null;
    final SelectionState selectionState = editor.getSelectionState();
    selectionState.setInsideChange(true);
    ComponentTreeBuilder builder = DesignerToolWindowManager.getInstance(editor).getComponentTreeBuilder();
    builder.beginUpdateSelection();

    try{
      final Stack<ComponentPtr[]> history = selectionState.getSelectionHistory();
      history.pop();
      SelectionState.restoreSelection(editor, history.peek());
    }
    finally {
      builder.endUpdateSelection();
      selectionState.setInsideChange(false);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void update(final @NotNull AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    final GuiEditor editor = FormEditingUtil.getEditorFromContext(e.getDataContext());
    if (editor == null) {
      presentation.setEnabled(false);
      return;
    }

    final Stack<ComponentPtr[]> history = editor.getSelectionState().getSelectionHistory();
    presentation.setEnabled(history.size() > 1);
  }
}
