// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.uiDesigner.CaptionSelection;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;


public abstract class RowColumnAction extends AnAction {
  private final @Nls String myColumnText;
  private final Icon myColumnIcon;
  private final @Nls String myRowText;
  private final Icon myRowIcon;

  public RowColumnAction(final @Nls String columnText, final @Nullable Icon columnIcon,
                         final @Nls String rowText, final @Nullable Icon rowIcon) {
    myColumnText = columnText;
    myColumnIcon = columnIcon;
    myRowText = rowText;
    myRowIcon = rowIcon;
  }

  @Override
  public void actionPerformed(final @NotNull AnActionEvent e) {
    GuiEditor editor = FormEditingUtil.getEditorFromContext(e.getDataContext());
    CaptionSelection selection = e.getData(CaptionSelection.DATA_KEY);
    if (editor == null || selection == null || !editor.ensureEditable()) {
      return;
    }
    actionPerformed(selection);
    selection.getContainer().revalidate();
    editor.refreshAndSave(true);
  }

  protected abstract void actionPerformed(CaptionSelection selection);

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void update(final @NotNull AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    CaptionSelection selection = e.getData(CaptionSelection.DATA_KEY);
    if (selection == null) {
      presentation.setEnabled(false);
    }
    else {
      presentation.setEnabled(selection.getContainer() != null && selection.getFocusedIndex() >= 0);
      if (!selection.isRow()) {
        presentation.setText(myColumnText);
        if (myColumnIcon != null) {
          presentation.setIcon(myColumnIcon);
        }
      }
      else {
        presentation.setText(myRowText);
        if (myRowIcon != null) {
          presentation.setIcon(myRowIcon);
        }
      }
    }
  }
}
