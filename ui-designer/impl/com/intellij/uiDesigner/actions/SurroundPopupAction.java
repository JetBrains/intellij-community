/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.FormEditingUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class SurroundPopupAction extends AbstractGuiEditorAction {
  private SurroundActionGroup myActionGroup = new SurroundActionGroup();

  protected void actionPerformed(final GuiEditor editor, final List<RadComponent> selection, final AnActionEvent e) {
    final ListPopup groupPopup = JBPopupFactory.getInstance()
      .createActionGroupPopup(UIDesignerBundle.message("surround.with.popup.title"), myActionGroup, e.getDataContext(),
                              JBPopupFactory.ActionSelectionAid.NUMBERING, true);

    RadComponent selComponent = selection.get(0);
    FormEditingUtil.showPopupUnderComponent(groupPopup, selComponent);
  }

  protected void update(@NotNull GuiEditor editor, final ArrayList<RadComponent> selection, final AnActionEvent e) {
    e.getPresentation().setEnabled(selection.size() > 0);
  }
}
