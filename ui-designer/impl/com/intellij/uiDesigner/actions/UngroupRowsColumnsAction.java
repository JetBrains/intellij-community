/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.actions;

import com.intellij.uiDesigner.CaptionSelection;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.jgoodies.forms.layout.FormLayout;

import java.util.List;
import java.util.ArrayList;

/**
 * @author yole
 */
public class UngroupRowsColumnsAction extends RowColumnAction {
  public UngroupRowsColumnsAction() {
    super(UIDesignerBundle.message("action.ungroup.columns"), null, UIDesignerBundle.message("action.ungroup.rows"), null);
  }

  @Override
  public void update(final AnActionEvent e) {
    super.update(e);
    CaptionSelection selection = (CaptionSelection) e.getDataContext().getData(CaptionSelection.class.getName());
    if (selection != null) {
      e.getPresentation().setEnabled(selection.getContainer() != null &&
        selection.getContainer().getLayout() instanceof FormLayout &&
        GroupRowsColumnsAction.isGrouped(selection));
    }
  }

  protected void actionPerformed(CaptionSelection selection) {
    FormLayout layout = (FormLayout) selection.getContainer().getLayout();
    int[][] oldGroups = selection.isRow() ? layout.getRowGroups() : layout.getColumnGroups();
    List<int[]> newGroups = new ArrayList<int[]>();
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
