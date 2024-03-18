// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.componentTree.ComponentTree;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;


public class SurroundPopupAction extends AbstractGuiEditorAction {
  private final SurroundActionGroup myActionGroup = new SurroundActionGroup();

  @Override
  protected void actionPerformed(final GuiEditor editor, final List<? extends RadComponent> selection, final AnActionEvent e) {
    final ListPopup groupPopup = JBPopupFactory.getInstance()
      .createActionGroupPopup(UIDesignerBundle.message("surround.with.popup.title"), myActionGroup, e.getDataContext(),
                              JBPopupFactory.ActionSelectionAid.ALPHA_NUMBERING, true);

    final JComponent component = (JComponent)e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);
    if (component instanceof ComponentTree) {
      groupPopup.show(JBPopupFactory.getInstance().guessBestPopupLocation(component));
    }
    else {
      RadComponent selComponent = selection.get(0);
      FormEditingUtil.showPopupUnderComponent(groupPopup, selComponent);
    }
  }

  @Override
  protected void update(@NotNull GuiEditor editor, final ArrayList<? extends RadComponent> selection, final AnActionEvent e) {
    e.getPresentation().setEnabled(!selection.isEmpty());
  }
}