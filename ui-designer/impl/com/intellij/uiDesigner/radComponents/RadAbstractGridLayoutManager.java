/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.GridChangeUtil;
import com.intellij.uiDesigner.designSurface.*;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author yole
 */
public abstract class RadAbstractGridLayoutManager extends RadLayoutManager {
  @Override
  public boolean isGrid() {
    return true;
  }

  public abstract int getGridRowCount(RadContainer container);
  public abstract int getGridColumnCount(RadContainer container);

  public int getGridRowAt(RadContainer container, int y) {
    return getGridCellAt(container, y, true);
  }

  public int getGridColumnAt(RadContainer container, int x) {
    return getGridCellAt(container, x, false);
  }

  private int getGridCellAt(final RadContainer container, final int coord, final boolean isRow) {
    int[] coords = getGridCellCoords(container, isRow);
    int[] sizes = getGridCellSizes(container, isRow);
    for (int i = 0; i < coords.length; i++) {
      if (coords[i] <= coord && coord <= coords[i] + sizes[i]) {
        return i;
      }
    }
    return -1;
  }

  public abstract int[] getHorizontalGridLines(RadContainer container);
  public abstract int[] getVerticalGridLines(RadContainer container);
  public abstract int[] getGridCellCoords(RadContainer container, boolean isRow);
  public abstract int[] getGridCellSizes(RadContainer container, boolean isRow);

  @Nullable
  public CustomPropertiesPanel getRowColumnPropertiesPanel(RadContainer container, boolean isRow, int[] selectedIndices) {
    return null;
  }

  @Nullable
  public static RadComponent getComponentAtGrid(RadContainer container, final int row, final int column) {
    // If the target cell is not empty does not allow drop.
    for(int i=0; i<container.getComponentCount(); i++){
      final RadComponent component = container.getComponent(i);
      if (component.isDragging()) {
        continue;
      }
      final GridConstraints constraints=component.getConstraints();
      if(
        constraints.getRow() <= row && row < constraints.getRow()+constraints.getRowSpan() &&
        constraints.getColumn() <= column && column < constraints.getColumn()+constraints.getColSpan()
      ){
        return component;
      }
    }
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

  public Rectangle getGridCellRangeRect(RadContainer container, int startRow, int startCol, int endRow, int endCol) {
    int[] xs = getGridCellCoords(container, false);
    int[] ys = getGridCellCoords(container, true);
    int[] widths = getGridCellSizes(container, false);
    int[] heights = getGridCellSizes(container, true);
    return new Rectangle(xs[startCol],
                         ys[startRow],
                         xs[endCol] + widths[endCol] - xs[startCol],
                         ys[endRow] + heights[endRow] - ys[startRow]);
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
  public int insertGridCells(final RadContainer grid, final int cellIndex, final boolean isRow, final boolean isBefore, final boolean grow) {
    GridChangeUtil.insertRowOrColumn(grid, cellIndex, isRow, isBefore);
    return 1;
  }

  public void copyGridCells(RadContainer source, final RadContainer destination, final boolean isRow, int cellIndex, int cellCount, int targetIndex) {
    for(int i=0; i< cellCount; i++) {
      insertGridCells(destination, cellIndex, isRow, false, false);
    }
  }

  /**
   * @return the number of deleted rows or columns
   */
  public int deleteGridCells(final RadContainer grid, final int cellIndex, final boolean isRow) {
    GridChangeUtil.deleteCell(grid, cellIndex, isRow);
    return 1;
  }

  public void processCellsMoved(final RadContainer container, final boolean isRow, final int[] cells, final int targetCell) {
    GridChangeUtil.moveCells(container, isRow, cells, targetCell);
  }

  public int getGapCellCount() {
    return 0;
  }

  public int getGapCellSize(final RadContainer container, boolean isRow) {
    return 0;
  }

  public boolean isGapCell(RadContainer grid, boolean isRow, int cellIndex) {
    return false;
  }

  public int getCellIndexBase() {
    return 0;
  }

  public boolean canResizeCells() {
    return true;
  }

  @Nullable
  public String getCellResizeTooltip(RadContainer container, boolean isRow, int cell, int newSize) {
    return null;
  }
  public void processCellResized(RadContainer container, final boolean isRow, final int cell, final int newSize) {
  }

  public abstract void copyGridSection(final RadContainer source, final RadContainer destination, final Rectangle rc);

  public LayoutManager copyLayout(LayoutManager layout, int rowDelta, int columnDelta) {
    return layout;
  }

  /**
   * Returns true if the dimensions of the grid in the container are defined by the coordinates of the components
   * in the grid (and therefore it must not be validated that an inserted component fits the grid bounds).
   */
  public boolean isGridDefinedByComponents() {
    return false;
  }

  protected static void writeGridConstraints(final XmlWriter writer, final RadComponent child) {
    // Constraints in Grid layout
    writer.startElement("grid");
    try {
      final GridConstraints constraints = child.getConstraints();
      writer.addAttribute("row",constraints.getRow());
      writer.addAttribute("column",constraints.getColumn());
      writer.addAttribute("row-span",constraints.getRowSpan());
      writer.addAttribute("col-span",constraints.getColSpan());
      writer.addAttribute("vsize-policy",constraints.getVSizePolicy());
      writer.addAttribute("hsize-policy",constraints.getHSizePolicy());
      writer.addAttribute("anchor",constraints.getAnchor());
      writer.addAttribute("fill",constraints.getFill());
      writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_INDENT, constraints.getIndent());
      writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_USE_PARENT_LAYOUT, constraints.isUseParentLayout());

      // preferred size
      writer.writeDimension(constraints.myMinimumSize,"minimum-size");
      writer.writeDimension(constraints.myPreferredSize,"preferred-size");
      writer.writeDimension(constraints.myMaximumSize,"maximum-size");
    } finally {
      writer.endElement(); // grid
    }
  }


  @Override @NotNull
  public DropLocation getDropLocation(RadContainer container, @Nullable final Point location) {
    if (container.getGridRowCount() == 1 && container.getGridColumnCount() == 1 &&
        getComponentAtGrid(container, 0, 0) == null) {
      final Rectangle rc = getGridCellRangeRect(container, 0, 0, 0, 0);
      if (location == null) {
        return new FirstComponentInsertLocation(container, rc, 0, 0);
      }
      return new FirstComponentInsertLocation(container, location, rc);
    }

    if (location == null) {
      if (getComponentAtGrid(container, 0, 0) == null) {
        return new GridDropLocation(container, 0, 0);
      }
      return new GridInsertLocation(container, getLastNonSpacerRow(container), 0, GridInsertMode.RowAfter);
    }

    int[] xs = getGridCellCoords(container, false);
    int[] ys = getGridCellCoords(container, true);
    int[] widths = getGridCellSizes(container, false);
    int[] heights = getGridCellSizes(container, true);

    int[] horzGridLines = getHorizontalGridLines(container);
    int[] vertGridLines = getVerticalGridLines(container);

    int row=ys.length-1;
    int col=xs.length-1;
    for(int i=0; i<xs.length; i++) {
      if (location.x < xs[i] + widths[i]) {
        col=i;
        break;
      }
    }
    for(int i=0; i<ys.length; i++) {
      if (location.getY() < ys [i]+heights [i]) {
        row=i;
        break;
      }
    }

    GridInsertMode mode = null;

    int EPSILON = 4;
    int dy = (int)(location.getY() - ys [row]);
    if (dy < EPSILON) {
      mode = GridInsertMode.RowBefore;
    }
    else if (heights [row] - dy < EPSILON) {
      mode = GridInsertMode.RowAfter;
    }

    int dx = location.x - xs[col];
    if (dx < EPSILON) {
      mode = GridInsertMode.ColumnBefore;
    }
    else if (widths [col] - dx < EPSILON) {
      mode = GridInsertMode.ColumnAfter;
    }

    final int cellWidth = vertGridLines[col + 1] - vertGridLines[col];
    final int cellHeight = horzGridLines[row + 1] - horzGridLines[row];
    if (mode == null) {
      RadComponent component = getComponentAtGrid(container, row, col);
      if (component != null) {
        Rectangle rc = component.getBounds();
        rc.translate(-xs [col], -ys [row]);

        int right = rc.x + rc.width + GridInsertLocation.INSERT_RECT_MIN_SIZE;
        int bottom = rc.y + rc.height + GridInsertLocation.INSERT_RECT_MIN_SIZE;

        if (dy < rc.y - GridInsertLocation.INSERT_RECT_MIN_SIZE) {
          mode = GridInsertMode.RowBefore;
        }
        else if (dy > bottom && dy < cellHeight) {
          mode = GridInsertMode.RowAfter;
        }
        if (dx < rc.x - GridInsertLocation.INSERT_RECT_MIN_SIZE) {
          mode = GridInsertMode.ColumnBefore;
        }
        else if (dx > right && dx < cellWidth) {
          mode = GridInsertMode.ColumnAfter;
        }
      }
    }

    if (mode != null) {
      return new GridInsertLocation(container, row, col, mode).normalize();
    }
    if (getComponentAtGrid(container, row, col) instanceof RadVSpacer ||
        getComponentAtGrid(container, row, col) instanceof RadHSpacer) {
      return new GridReplaceDropLocation(container, row, col);
    }
    return new GridDropLocation(container, row, col);
  }

  private int getLastNonSpacerRow(final RadContainer container) {
    int lastRow = getGridRowCount(container)-1;
    for(int col=0; col<getGridColumnCount(container); col++) {
      RadComponent c = getComponentAtGrid(container, lastRow, col);
      if (c != null && !(c instanceof RadHSpacer) && !(c instanceof RadVSpacer)) {
        return lastRow;
      }
    }
    return lastRow-1;
  }

  protected static void drawGrowMarker(final boolean isRow, final Graphics2D g2d, final Rectangle rc) {
    g2d.setColor(Color.BLACK);
    if (!isRow) {
      int maxX = (int) rc.getMaxX();
      int midY = (int) rc.getCenterY()+3;
      final int xStart = Math.max(maxX - 10, rc.x + 2);
      final int xEnd = maxX - 2;
      g2d.drawLine(xStart, midY, xEnd, midY);
      g2d.drawLine(xStart, midY, xStart+2, midY-2);
      g2d.drawLine(xStart, midY, xStart+2, midY+2);
      g2d.drawLine(xEnd, midY, xEnd-2, midY-2);
      g2d.drawLine(xEnd, midY, xEnd-2, midY+2);
    }
    else {
      int maxY = (int) rc.getMaxY();
      int midX = (int) rc.getCenterX()+3;
      final int yStart = Math.max(maxY - 10, rc.y + 2);
      final int yEnd = maxY - 2;
      g2d.drawLine(midX, yStart, midX, yEnd);
      g2d.drawLine(midX, yStart, midX-2, yStart+2);
      g2d.drawLine(midX, yStart, midX+2, yStart+2);
      g2d.drawLine(midX, yEnd, midX-2, yEnd-2);
      g2d.drawLine(midX, yEnd, midX+2, yEnd-2);
    }
  }
}
