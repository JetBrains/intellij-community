/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.actionSystem.ActionGroup;
import org.jetbrains.annotations.Nullable;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;

/**
 * @author yole
 */
public abstract class RadAbstractGridLayoutManager extends RadLayoutManager {
  @Override
  public boolean isGrid() {
    return true;
  }

  @Nullable public abstract RadComponent getComponentAtGrid(RadContainer container, final int row, final int column);
  public abstract int getGridRowCount(RadContainer container);
  public abstract int getGridColumnCount(RadContainer container);
  public abstract int getGridRowAt(RadContainer container, int y);
  public abstract int getGridColumnAt(RadContainer container, int x);
  public abstract Rectangle getGridCellRangeRect(RadContainer container, int startRow, int startCol, int endRow, int endCol);
  public abstract int[] getHorizontalGridLines(RadContainer container);
  public abstract int[] getVerticalGridLines(RadContainer container);
  public abstract int[] getGridCellCoords(RadContainer container, boolean isRow);
  public abstract int[] getGridCellSizes(RadContainer container, boolean isRow);

  @Nullable
  public RowColumnPropertiesPanel getRowColumnPropertiesPanel(RadContainer container, boolean isRow, int[] selectedIndices) {
    return null;
  }

  public int getGridLineNear(RadContainer container, boolean isRow, Point pnt, int epsilon) {
    int coord = isRow ? pnt.y : pnt.x;
    int[] gridLines = isRow ? getHorizontalGridLines(container) : getVerticalGridLines(container);
    for(int col = 1; col <gridLines.length; col++) {
      if (coord < gridLines [col]) {
        if (coord - gridLines [col-1] < epsilon) {
          return col-1;
        }
        if (gridLines [col] - coord < epsilon) {
          return col;
        }
        return -1;
      }
    }
    if (coord - gridLines [gridLines.length-1] < epsilon) {
      return gridLines.length-1;
    }
    return -1;
  }

  @Nullable
  public ActionGroup getCaptionActions() {
    return null;
  }

  public void paintCaptionDecoration(final RadContainer container, final boolean isRow, final int i, final Graphics2D g2d,
                                     final Rectangle rc) {
  }

  /**
   * @return the number of inserted rows or columns
   */
  public abstract int insertGridCells(final RadContainer grid, final int cellIndex, final boolean isRow, final boolean isBefore, final boolean grow);
  public abstract void copyGridRows(RadContainer grid, int rowIndex, int rowCount, int targetIndex);
  public int getGapCellCount() {
    return 0;
  }
  public boolean isGapCell(RadContainer grid, boolean isRow, int cellIndex) {
    return false;
  }

  /**
   * @return the number of deleted rows or columns
   */
  public abstract int deleteGridCells(final RadContainer grid, final int cellIndex, final boolean isRow);
  public abstract void processCellsMoved(final RadContainer container, final boolean isRow, final int[] cells, final int targetCell);
  public abstract void processCellResized(RadContainer container, final boolean isRow, final int cell, final int newSize);
}
