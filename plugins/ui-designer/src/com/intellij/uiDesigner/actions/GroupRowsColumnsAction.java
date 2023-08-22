// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.uiDesigner.CaptionSelection;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.jgoodies.forms.layout.FormLayout;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;


public class GroupRowsColumnsAction extends RowColumnAction {
  public GroupRowsColumnsAction() {
    super(UIDesignerBundle.message("action.group.columns"), null, UIDesignerBundle.message("action.group.rows"), null);
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    super.update(e);
    CaptionSelection selection = e.getData(CaptionSelection.DATA_KEY);
    if (selection != null) {
      e.getPresentation().setEnabled(selection.getContainer() != null &&
        selection.getContainer().getLayout() instanceof FormLayout &&
        getCellsToGroup(selection).length > 1 &&
        !isGrouped(selection));
    }
  }

  public static boolean isGrouped(final CaptionSelection selection) {
    FormLayout layout = (FormLayout) selection.getContainer().getLayout();
    int[][] groups = selection.isRow() ? layout.getRowGroups() : layout.getColumnGroups();
    final int[] indices = selection.getSelection();
    for (int[] group : groups) {
      if (intersect(group, indices)) return true;
    }
    return false;
  }

  public static boolean intersect(final int[] group, final int[] indices) {
    for (int groupMember : group) {
      for (int index : indices) {
        if (groupMember == index+1) return true;
      }
    }
    return false;
  }

  @Override
  protected void actionPerformed(CaptionSelection selection) {
    FormLayout layout = (FormLayout) selection.getContainer().getLayout();
    int[][] oldGroups = selection.isRow() ? layout.getRowGroups() : layout.getColumnGroups();
    int[][] newGroups = new int[oldGroups.length + 1][];
    System.arraycopy(oldGroups, 0, newGroups, 0, oldGroups.length);
    int[] cellsToGroup = getCellsToGroup(selection);
    newGroups [oldGroups.length] = new int [cellsToGroup.length];
    for(int i=0; i<cellsToGroup.length; i++) {
      newGroups [oldGroups.length] [i] = cellsToGroup [i]+1;
    }
    if (selection.isRow()) {
      layout.setRowGroups(newGroups);
    }
    else {
      layout.setColumnGroups(newGroups);
    }
  }

  private static int[] getCellsToGroup(CaptionSelection selection) {
    ArrayList<Integer> cells = new ArrayList<>();
    int[] selectedIndices = selection.getSelection();
    for(int i: selectedIndices) {
      if (!selection.getContainer().getGridLayoutManager().isGapCell(selection.getContainer(), selection.isRow(), i)) {
        cells.add(i);
      }
    }
    int[] result = new int[cells.size()];
    for(int i=0; i<cells.size(); i++) {
      result [i] = cells.get(i).intValue();
    }
    return result;
  }
}
