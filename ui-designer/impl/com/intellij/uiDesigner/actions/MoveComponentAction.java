/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.radComponents.RadComponent;
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
      constraints.setRow(constraints.getRow() + myRowDelta);
      constraints.setColumn(constraints.getColumn() + myColumnDelta);
      constraints.setRowSpan(constraints.getRowSpan() + myRowSpanDelta);
      constraints.setColSpan(constraints.getColSpan() + myColSpanDelta);
      c.fireConstraintsChanged(oldConstraints);
    }
  }

  @Override
  protected void update(@NotNull GuiEditor editor, final ArrayList<RadComponent> selection, final AnActionEvent e) {
    for(RadComponent c: selection) {
      if (!c.getParent().getLayoutManager().isGrid()) {
        e.getPresentation().setEnabled(false);
        return;
      }
      GridConstraints constraints = c.getConstraints();
      final int newRow = constraints.getRow() + myRowDelta;
      final int newCol = constraints.getColumn() + myColumnDelta;
      final int newRowSpan = constraints.getRowSpan() + myRowSpanDelta;
      final int newColSpan = constraints.getColSpan() + myColSpanDelta;
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
