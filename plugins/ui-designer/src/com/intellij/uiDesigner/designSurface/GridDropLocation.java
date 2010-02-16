/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.uiDesigner.designSurface;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.shared.XYLayoutManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.Rectangle;
import java.awt.LayoutManager;

/**
 * @author yole
 */
public class GridDropLocation implements ComponentDropLocation {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.designSurface.GridDropLocation");

  protected final RadContainer myContainer;
  protected int myRow;
  protected int myColumn;

  public GridDropLocation(@NotNull final RadContainer container, final int row, final int column) {
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

  public RadContainer getContainer() {
    return myContainer;
  }

  public boolean canDrop(final ComponentDragObject dragObject) {
    // If target point doesn't belong to any cell and column then do not allow drop.
    if (myRow == -1 || myColumn == -1) {
      LOG.debug("RadContainer.canDrop=false because no cell at mouse position");
      return false;
    }

    for(int i=0; i<dragObject.getComponentCount(); i++) {
      int relativeCol = dragObject.getRelativeCol(i);
      int relativeRow = dragObject.getRelativeRow(i);
      int colSpan = dragObject.getColSpan(i);
      int rowSpan = dragObject.getRowSpan(i);

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

  public void placeFeedback(FeedbackLayer feedbackLayer, ComponentDragObject dragObject) {
    Rectangle feedbackRect = null;
    if (getContainer().getLayoutManager().isGrid()) {
      feedbackRect = getGridFeedbackRect(dragObject, false, false);
    }
    if (feedbackRect != null) {
      final JComponent component = getContainer().getDelegee();
      StringBuilder feedbackBuilder = new StringBuilder(getContainer().getDisplayName());
      feedbackBuilder.append(" (").append(myRow + getContainer().getGridLayoutManager().getCellIndexBase());
      feedbackBuilder.append(", ").append(myColumn + getContainer().getGridLayoutManager().getCellIndexBase());
      feedbackBuilder.append(")");
      feedbackLayer.putFeedback(component, feedbackRect, feedbackBuilder.toString());
    }
    else {
      feedbackLayer.removeFeedback();
    }
  }

  @Nullable
  protected Rectangle getGridFeedbackCellRect(ComponentDragObject dragObject, final boolean ignoreWidth, final boolean ignoreHeight) {
    if (dragObject.getComponentCount() == 0) {
      LOG.debug("no feedback rect because component count=0");
      return null;
    }

    Rectangle rc = getDragObjectDimensions(dragObject);
    int w = ignoreWidth ? 1 : rc.width;
    int h = ignoreHeight ? 1 : rc.height;

    if (rc.x < 0 || rc.y < 0 ||
        rc.y + h > getContainer().getGridRowCount() || rc.x + w > getContainer().getGridColumnCount()) {
      LOG.debug("no feedback rect because insert range is outside grid: firstRow=" + rc.y +
                ", firstCol=" + rc.x + ", lastRow=" + (rc.y+h-1) + ", lastCol=" + (rc.x+w-1));
      return null;
    }
    return new Rectangle(rc.x, rc.y, w-1, h-1);
  }

  protected Rectangle getDragObjectDimensions(final ComponentDragObject dragObject) {
    int firstRow = getRow();
    int lastRow = getRow();
    int firstCol = getColumn();
    int lastCol = getColumn();
    for(int i=0; i<dragObject.getComponentCount(); i++) {
      final int relRow = dragObject.getRelativeRow(i);
      final int relCol = dragObject.getRelativeCol(i);
      firstRow = Math.min(firstRow, getRow() + relRow);
      firstCol = Math.min(firstCol, getColumn() + relCol);
      lastRow = Math.max(lastRow, getRow() + relRow + dragObject.getRowSpan(i) - 1);
      lastCol = Math.max(lastCol, getColumn() + relCol + dragObject.getColSpan(i) - 1);
    }
    return new Rectangle(firstCol, firstRow, lastCol-firstCol+1, lastRow-firstRow+1);
  }

  @Nullable
  protected Rectangle getGridFeedbackRect(ComponentDragObject dragObject, final boolean ignoreWidth, final boolean ignoreHeight) {
    Rectangle cellRect = getGridFeedbackCellRect(dragObject, ignoreWidth, ignoreHeight);
    if (cellRect == null) return null;
    int h = ignoreHeight ? 0 : cellRect.height;
    int w = ignoreWidth ? 0 : cellRect.width;
    return getContainer().getGridLayoutManager().getGridCellRangeRect(getContainer(), cellRect.y, cellRect.x,
                                                                      cellRect.y+h, cellRect.x+w);
  }

  public void processDrop(final GuiEditor editor,
                          final RadComponent[] components,
                          final GridConstraints[] constraintsToAdjust,
                          final ComponentDragObject dragObject) {
    dropIntoGrid(myContainer, components, myRow, myColumn, dragObject);
  }

  @Nullable
  public ComponentDropLocation getAdjacentLocation(Direction direction) {
    switch(direction) {
      case LEFT:  return new GridInsertLocation(myContainer, myRow, myColumn, GridInsertMode.ColumnBefore);
      case UP:    return new GridInsertLocation(myContainer, myRow, myColumn, GridInsertMode.RowBefore);
      case RIGHT: return new GridInsertLocation(myContainer, myRow, myColumn, GridInsertMode.ColumnAfter);
      case DOWN:  return new GridInsertLocation(myContainer, myRow, myColumn, GridInsertMode.RowAfter); 
    }
    return null;
  }

  @NonNls @Override public String toString() {
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
      container.addComponent(c);

      // Fill DropInfo
      c.revalidate();
    }

    container.revalidate();
  }
}
