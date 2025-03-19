// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.designSurface;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.shared.XYLayoutManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;


public class GridDropLocation implements ComponentDropLocation {
  private static final Logger LOG = Logger.getInstance(GridDropLocation.class);

  protected final RadContainer myContainer;
  protected int myRow;
  protected int myColumn;

  public GridDropLocation(final @NotNull RadContainer container, final int row, final int column) {
    myContainer = container;
    myRow = row;
    myColumn = column;
  }

  public int getRow() {
    return myRow;
  }

  public int getColumn() {
    return myColumn;
  }

  @Override
  public RadContainer getContainer() {
    return myContainer;
  }

  @Override
  public boolean canDrop(final ComponentDragObject dragObject) {
    // If target point doesn't belong to any cell and column then do not allow drop.
    if (myRow == -1 || myColumn == -1) {
      LOG.debug("RadContainer.canDrop=false because no cell at mouse position");
      return false;
    }

    int colSpan = 1; // allow drop any (NxM) component to cell (1x1)
    int rowSpan = 1;

    for(int i=0; i<dragObject.getComponentCount(); i++) {
      int relativeCol = dragObject.getRelativeCol(i);
      int relativeRow = dragObject.getRelativeRow(i);

      LOG.debug("checking component: relativeRow" + relativeRow + ", relativeCol" + relativeCol + ", colSpan=" + colSpan + ", rowSpan=" + rowSpan);

      if (myRow + relativeRow < 0 ||
          myColumn + relativeCol < 0 ||
          myRow + relativeRow + rowSpan > myContainer.getGridRowCount() ||
          myColumn + relativeCol + colSpan > myContainer.getGridColumnCount()) {
        LOG.debug("RadContainer.canDrop=false because range is outside grid: row=" + (myRow +relativeRow) +
                  ", col=" + (myColumn +relativeCol) + ", colSpan=" + colSpan + ", rowSpan=" + rowSpan);
        return false;
      }

      final RadComponent componentInRect = findOverlappingComponent(myRow + relativeRow, myColumn + relativeCol, rowSpan, colSpan);
      if (componentInRect != null) {
        LOG.debug("GridDropLocation.canDrop=false because found component " + componentInRect.getId() +
                  " in rect (row=" + (myRow +relativeRow) + ", col=" + (myColumn +relativeCol) +
                  ", rowSpan=" + rowSpan + ", colSpan=" + colSpan + ")");
        return false;
      }
    }
    LOG.debug("canDrop=true");
    return true;
  }

  protected RadComponent findOverlappingComponent(final int startRow, final int startCol, final int rowSpan, final int colSpan) {
    return myContainer.findComponentInRect(startRow, startCol, rowSpan, colSpan);
  }

  @Override
  public void placeFeedback(FeedbackLayer feedbackLayer, ComponentDragObject dragObject) {
    Rectangle feedbackRect = null;
    if (getContainer().getLayoutManager().isGrid()) {
      feedbackRect = getGridFeedbackRect(dragObject, false, false, true);
    }
    if (feedbackRect != null) {
      final JComponent component = getContainer().getDelegee();
      String feedback = getContainer().getDisplayName() + " (" + 
                        (myRow + getContainer().getGridLayoutManager().getCellIndexBase()) + ", " + 
                        (myColumn + getContainer().getGridLayoutManager().getCellIndexBase()) + ")";
      feedbackLayer.putFeedback(component, feedbackRect, feedback);
    }
    else {
      feedbackLayer.removeFeedback();
    }
  }

  protected @Nullable Rectangle getGridFeedbackCellRect(ComponentDragObject dragObject,
                                                        boolean ignoreWidth,
                                                        boolean ignoreHeight,
                                                        boolean overlapping) {
    if (dragObject.getComponentCount() == 0) {
      return null;
    }

    Rectangle rc = calculateGridFeedbackCellRect(dragObject, ignoreWidth, ignoreHeight, true);

    if (rc == null) {
      return calculateGridFeedbackCellRect(dragObject, ignoreWidth, ignoreHeight, false);
    }

    if (overlapping && findOverlappingComponent(rc.y, rc.x, rc.height + 1, rc.width + 1) != null) {
     return calculateGridFeedbackCellRect(dragObject, ignoreWidth, ignoreHeight, false);
    }

    return rc;
  }

  private @Nullable Rectangle calculateGridFeedbackCellRect(ComponentDragObject dragObject,
                                                            boolean ignoreWidth,
                                                            boolean ignoreHeight,
                                                            boolean spans) {
    Rectangle rc = getDragObjectDimensions(dragObject, spans);
    int w = ignoreWidth ? 1 : rc.width;
    int h = ignoreHeight ? 1 : rc.height;

    if (rc.x < 0 || rc.y < 0 || rc.y + h > getContainer().getGridRowCount() || rc.x + w > getContainer().getGridColumnCount()) {
      return null;
    }

    return new Rectangle(rc.x, rc.y, w - 1, h - 1);
  }

  protected Rectangle getDragObjectDimensions(ComponentDragObject dragObject, boolean spans) {
    int firstRow = getRow();
    int lastRow = getRow();
    int firstCol = getColumn();
    int lastCol = getColumn();
    for (int i = 0; i < dragObject.getComponentCount(); i++) {
      int relRow = dragObject.getRelativeRow(i);
      int relCol = dragObject.getRelativeCol(i);
      firstRow = Math.min(firstRow, getRow() + relRow);
      firstCol = Math.min(firstCol, getColumn() + relCol);
      lastRow = Math.max(lastRow, getRow() + relRow + (spans ? dragObject.getRowSpan(i) : 1) - 1);
      lastCol = Math.max(lastCol, getColumn() + relCol + (spans ? dragObject.getColSpan(i) : 1) - 1);
    }
    return new Rectangle(firstCol, firstRow, lastCol - firstCol + 1, lastRow - firstRow + 1);
  }

  protected @Nullable Rectangle getGridFeedbackRect(ComponentDragObject dragObject, boolean ignoreWidth, boolean ignoreHeight, boolean overlapping) {
    Rectangle cellRect = getGridFeedbackCellRect(dragObject, ignoreWidth, ignoreHeight, overlapping);
    if (cellRect == null) return null;
    int h = ignoreHeight ? 0 : cellRect.height;
    int w = ignoreWidth ? 0 : cellRect.width;
    return getContainer().getGridLayoutManager().getGridCellRangeRect(getContainer(), cellRect.y, cellRect.x,
                                                                      cellRect.y+h, cellRect.x+w);
  }

  @Override
  public void processDrop(final GuiEditor editor,
                          final RadComponent[] components,
                          final GridConstraints[] constraintsToAdjust,
                          final ComponentDragObject dragObject) {
    dropIntoGrid(myContainer, components, myRow, myColumn, dragObject);
  }

  @Override
  public @Nullable ComponentDropLocation getAdjacentLocation(Direction direction) {
    var insertMode = switch (direction) {
      case LEFT -> GridInsertMode.ColumnBefore;
      case UP -> GridInsertMode.RowBefore;
      case RIGHT -> GridInsertMode.ColumnAfter;
      case DOWN -> GridInsertMode.RowAfter;
    };
    return new GridInsertLocation(myContainer, myRow, myColumn, insertMode);
  }

  @Override
  public @NonNls String toString() {
    return "GridDropLocation(row=" + myRow + ",col=" + myColumn + ")";
  }

  protected static void dropIntoGrid(final RadContainer container, final RadComponent[] components, int row, int column, final ComponentDragObject dragObject) {
    assert components.length > 0;

    for(int i=0; i<components.length; i++) {
      RadComponent c = components [i];
      if (c instanceof RadContainer) {
        final LayoutManager layout = ((RadContainer)c).getLayout();
        if (layout instanceof XYLayoutManager) {
          ((XYLayoutManager)layout).setPreferredSize(c.getSize());
        }
      }

      int relativeCol = dragObject.getRelativeCol(i);
      int relativeRow = dragObject.getRelativeRow(i);
      LOG.debug("dropIntoGrid: relativeRow=" + relativeRow + ", relativeCol=" + relativeCol);
      int colSpan = dragObject.getColSpan(i);
      int rowSpan = dragObject.getRowSpan(i);

      assert row + relativeRow >= 0;
      assert column + relativeCol >= 0;

      if (rowSpan > 1 || colSpan > 1) {
        if ((row + relativeRow + rowSpan > container.getGridRowCount() && rowSpan > 1) ||
            (column + relativeCol + colSpan > container.getGridColumnCount() && colSpan > 1) ||
            container.findComponentInRect(row + relativeRow, column + relativeCol, rowSpan, colSpan) != null) {
          rowSpan = 1;
          colSpan = 1;
        }
      }

      if (!container.getGridLayoutManager().isGridDefinedByComponents()) {
        assert relativeRow + rowSpan <= container.getGridRowCount();
        assert relativeCol + colSpan <= container.getGridColumnCount();
      }

      RadComponent old = container.findComponentInRect(row + relativeRow, column + relativeCol, rowSpan, colSpan);
      if (old != null) {
        LOG.error("Drop rectangle not empty: (" + (row + relativeRow) + ", " + (column + relativeCol) + ", " + rowSpan + ", " + colSpan +
                  "), component ID=" + old.getId());
      }

      final GridConstraints constraints = c.getConstraints();
      constraints.setRow(row + relativeRow);
      constraints.setColumn(column + relativeCol);
      constraints.setRowSpan(rowSpan);
      constraints.setColSpan(colSpan);
      LOG.info("GridDropLocation.dropIntoGrid() constraints=" + constraints);
      container.addComponent(c);

      // Fill DropInfo
      c.revalidate();
    }

    container.revalidate();
    LOG.info("GridDropLocation.dropIntoGrid() done");
  }
}
