/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.uiDesigner.quickFixes;

import com.intellij.openapi.actionSystem.*;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.propertyInspector.DesignerToolWindowManager;
import com.intellij.uiDesigner.propertyInspector.PropertyInspector;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
final class ShowHintAction extends AnAction {
  private final QuickFixManager myManager;

  public ShowHintAction(@NotNull final QuickFixManager manager, @NotNull final JComponent component) {
    myManager = manager;
    registerCustomShortcutSet(
      ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS).getShortcutSet(),
      component
    );
  }

  public void actionPerformed(final AnActionEvent e) {
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

  public void update(AnActionEvent e) {
    // Alt-Enter hotkey for editor takes precedence over this action
    e.getPresentation().setEnabled(e.getData(CommonDataKeys.EDITOR) == null);
  }
}
