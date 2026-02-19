// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.uiDesigner.CaptionSelection;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.jgoodies.forms.layout.FormLayout;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;


public class UngroupRowsColumnsAction extends RowColumnAction {
  public UngroupRowsColumnsAction() {
    super(UIDesignerBundle.message("action.ungroup.columns"), null, UIDesignerBundle.message("action.ungroup.rows"), null);
  }

  @Override
  public void update(final @NotNull AnActionEvent e) {
    super.update(e);
    CaptionSelection selection = e.getData(CaptionSelection.DATA_KEY);
    if (selection != null) {
      e.getPresentation().setEnabled(selection.getContainer() != null &&
        selection.getContainer().getLayout() instanceof FormLayout &&
        GroupRowsColumnsAction.isGrouped(selection));
    }
  }

  @Override
  protected void actionPerformed(CaptionSelection selection) {
    FormLayout layout = (FormLayout) selection.getContainer().getLayout();
    int[][] oldGroups = selection.isRow() ? layout.getRowGroups() : layout.getColumnGroups();
    List<int[]> newGroups = new ArrayList<>();
    int[] selInts = selection.getSelection();
    for(int[] group: oldGroups) {
      if (!GroupRowsColumnsAction.intersect(group, selInts)) {
        newGroups.add(group);
      }
    }
    int[][] newGroupArray = newGroups.toArray(new int[newGroups.size()][]);
    if (selection.isRow()) {
      layout.setRowGroups(newGroupArray);
    }
    else {
      layout.setColumnGroups(newGroupArray);
    }
  }
}
