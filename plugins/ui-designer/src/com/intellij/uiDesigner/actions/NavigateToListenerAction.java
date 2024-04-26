// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.designSurface.ListenerNavigateButton;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;


public class NavigateToListenerAction extends AbstractGuiEditorAction {
  @Override
  protected void actionPerformed(final GuiEditor editor, final List<? extends RadComponent> selection, final AnActionEvent e) {
    ListenerNavigateButton.showNavigatePopup(selection.get(0), true);
  }

  @Override protected void update(@NotNull GuiEditor editor, final ArrayList<? extends RadComponent> selection, final AnActionEvent e) {
    e.getPresentation().setEnabled(!selection.isEmpty());
  }
}
