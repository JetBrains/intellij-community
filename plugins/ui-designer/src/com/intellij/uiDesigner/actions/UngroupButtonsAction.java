// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.IButtonGroup;
import com.intellij.uiDesigner.radComponents.RadButtonGroup;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class UngroupButtonsAction extends AbstractGuiEditorAction {
  public UngroupButtonsAction() {
    super(true);
  }

  @Override
  protected void actionPerformed(final GuiEditor editor, final List<? extends RadComponent> selection, final AnActionEvent e) {
    if (selection.size() == 1) {
      final RadComponent component = selection.get(0);
      IButtonGroup group = FormEditingUtil.findGroupForComponent(editor.getRootContainer(), component);
      editor.getRootContainer().deleteGroup((RadButtonGroup)group);
    }
    else {
      for (RadComponent component : selection) {
        editor.getRootContainer().setGroupForComponent(component, null);
      }
    }
  }

  @Override
  protected void update(final @NotNull GuiEditor editor, final ArrayList<? extends RadComponent> selection, final AnActionEvent e) {
    boolean visible = GroupButtonsAction.allButtons(selection);
    e.getPresentation().setVisible(visible);
    e.getPresentation().setEnabled(visible && canUngroup(editor, selection));
  }

  private static boolean canUngroup(final GuiEditor editor, final List<? extends RadComponent> selectedComponents) {
    if (selectedComponents.size() < 2) {
      return selectedComponents.size() == 1;
    }
    return isSameGroup(editor, selectedComponents);
  }

  public static boolean isSameGroup(final GuiEditor editor, final List<? extends RadComponent> selectedComponents) {
    final RadRootContainer rootContainer = editor.getRootContainer();
    IButtonGroup group = FormEditingUtil.findGroupForComponent(rootContainer, selectedComponents.get(0));
    if (group == null) {
      return false;
    }
    for(int i=1; i<selectedComponents.size(); i++) {
      if (FormEditingUtil.findGroupForComponent(rootContainer, selectedComponents.get(i)) != group) {
        return false;
      }
    }
    return true;
  }
}
