// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.quickFixes;

import com.intellij.openapi.actionSystem.*;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.propertyInspector.DesignerToolWindowManager;
import com.intellij.uiDesigner.propertyInspector.PropertyInspector;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

final class ShowHintAction extends AnAction {
  private final QuickFixManager myManager;

  ShowHintAction(@NotNull final QuickFixManager manager, @NotNull final JComponent component) {
    myManager = manager;
    registerCustomShortcutSet(
      ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS).getShortcutSet(),
      component
    );
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
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
  public void update(@NotNull AnActionEvent e) {
    // Alt-Enter hotkey for editor takes precedence over this action
    e.getPresentation().setEnabled(e.getData(CommonDataKeys.EDITOR) == null);
  }
}
