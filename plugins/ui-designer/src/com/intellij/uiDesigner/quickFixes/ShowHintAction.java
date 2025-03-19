// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.quickFixes;

import com.intellij.openapi.actionSystem.*;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.propertyInspector.DesignerToolWindowManager;
import com.intellij.uiDesigner.propertyInspector.PropertyInspector;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

final class ShowHintAction extends AnAction {
  private final QuickFixManager myManager;

  ShowHintAction(final @NotNull QuickFixManager manager) {
    myManager = manager;
  }

  void registerShortcutSet(@NotNull JComponent component) {
    registerCustomShortcutSet(
      ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS).getShortcutSet(),
      component
    );
  }

  @Override
  public void actionPerformed(final @NotNull AnActionEvent e) {
    final GuiEditor editor = myManager.getEditor();
    if (editor == null) return;

    // 1. Show light bulb
    myManager.showIntentionHint();

    // 2. Commit possible non committed value and show popup
    final PropertyInspector propertyInspector = DesignerToolWindowManager.getInstance(myManager.getEditor()).getPropertyInspector();
    if(propertyInspector != null && propertyInspector.isEditing()) {
      propertyInspector.stopEditing();
    }
    myManager.showIntentionPopup();
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    // Alt-Enter hotkey for editor takes precedence over this action
    e.getPresentation().setEnabled(e.getData(CommonDataKeys.EDITOR) == null);
  }
}
