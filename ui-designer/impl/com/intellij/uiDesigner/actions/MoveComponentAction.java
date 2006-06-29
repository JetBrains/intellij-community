/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.FormEditingUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class MoveComponentAction extends AbstractGuiEditorAction {
  private int myRowDelta;
  private int myColumnDelta;
  private final int myRowSpanDelta;
  private final int myColSpanDelta;

  public MoveComponentAction(final int rowDelta, final int columnDelta, final int rowSpanDelta, final int colSpanDelta) {
    super(true);
    myRowDelta = rowDelta;
    myColumnDelta = columnDelta;
    myRowSpanDelta = rowSpanDelta;
    myColSpanDelta = colSpanDelta;
  }

  protected void actionPerformed(final GuiEditor editor, final List<RadComponent> selection, final AnActionEvent e) {
    for(RadComponent c: selection) {
      GridConstraints constraints = c.getConstraints();
      GridConstraints oldConstraints = (GridConstraints)constraints.clone();
      constraints.setRow(getNewRow(c));
      constraints.setColumn(getNewColumn(c));
      constraints.setRowSpan(getNewRowSpan(c));
      constraints.setColSpan(getNewColSpan(c));
      c.fireConstraintsChanged(oldConstraints);
    }
  }

  private int getNewRow(final RadComponent c) {
    return FormEditingUtil.adjustForGap(c.getParent(), c.getConstraints().getRow() + myRowDelta, true, myRowDelta);
  }

  private int getNewColumn(final RadComponent c) {
    return FormEditingUtil.adjustForGap(c.getParent(), c.getConstraints().getColumn() + myColumnDelta, false, myColumnDelta);
  }

  private int getNewRowSpan(final RadComponent c) {
    int gapCount = c.getParent().getGridLayoutManager().getGapCellCount();
    return c.getConstraints().getRowSpan() + myRowSpanDelta * (gapCount+1);
  }

  private int getNewColSpan(final RadComponent c) {
    int gapCount = c.getParent().getGridLayoutManager().getGapCellCount();
    return c.getConstraints().getColSpan() + myColSpanDelta * (gapCount+1);
  }

  @Override
  protected void update(@NotNull GuiEditor editor, final ArrayList<RadComponent> selection, final AnActionEvent e) {
    for(RadComponent c: selection) {
      if (!c.getParent().getLayoutManager().isGrid()) {
        e.getPresentation().setEnabled(false);
        return;
      }
      final int newRow = getNewRow(c);
      final int newCol = getNewColumn(c);
      final int newRowSpan = getNewRowSpan(c);
      final int newColSpan = getNewColSpan(c);
      if (newRow < 0 || newCol < 0 || newRowSpan < 1 || newColSpan < 1 ||
          newRow + newRowSpan > c.getParent().getGridRowCount() ||
          newCol + newColSpan > c.getParent().getGridColumnCount()) {
        e.getPresentation().setEnabled(false);
        return;
      }
      c.setDragging(true);
      final RadComponent overlap = c.getParent().findComponentInRect(newRow, newCol, newRowSpan, newColSpan);
      c.setDragging(false);
      if (overlap != null) {
        e.getPresentation().setEnabled(false);
        return;
      }
    }
  }
}
