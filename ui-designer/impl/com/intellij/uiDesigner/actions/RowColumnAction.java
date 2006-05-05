/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.util.IconLoader;
import com.intellij.uiDesigner.CaptionSelection;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import org.jetbrains.annotations.NonNls;

/**
 * @author yole
 */
public abstract class RowColumnAction extends AnAction {
  private String myColumnText;
  private String myColumnIcon;
  private String myRowText;
  private String myRowIcon;

  public RowColumnAction(final String columnText, @NonNls final String columnIcon,
                         final String rowText, @NonNls final String rowIcon) {
    myColumnText = columnText;
    myColumnIcon = columnIcon;
    myRowText = rowText;
    myRowIcon = rowIcon;
  }

  public void actionPerformed(final AnActionEvent e) {
    GuiEditor editor = FormEditingUtil.getEditorFromContext(e.getDataContext());
    CaptionSelection selection = (CaptionSelection) e.getDataContext().getData(CaptionSelection.class.getName());
    if (editor == null || selection == null || !editor.ensureEditable()) {
      return;
    }
    actionPerformed(selection);
    selection.getContainer().revalidate();
    editor.refreshAndSave(true);
  }

  protected abstract void actionPerformed(CaptionSelection selection);

  public void update(final AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    CaptionSelection selection = (CaptionSelection) e.getDataContext().getData(CaptionSelection.class.getName());
    if (selection == null) {
      presentation.setEnabled(false);
    }
    else {
      presentation.setEnabled(selection.getContainer() != null);
      if (!selection.isRow()) {
        presentation.setText(myColumnText);
        if (myColumnIcon != null) {
          presentation.setIcon(IconLoader.getIcon(myColumnIcon));
        }
      }
      else {
        presentation.setText(myRowText);
        if (myRowIcon != null) {
          presentation.setIcon(IconLoader.getIcon(myRowIcon));
        }
      }
    }
  }
}
