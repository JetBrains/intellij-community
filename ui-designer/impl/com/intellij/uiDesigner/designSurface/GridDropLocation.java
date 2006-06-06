package com.intellij.uiDesigner.designSurface;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.shared.XYLayoutManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.Rectangle;
import java.awt.LayoutManager;

/**
 * @author yole
 */
public class GridDropLocation implements DropLocation {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.designSurface.GridDropLocation");

  protected RadContainer myContainer;
  protected int myRow;
  protected int myColumn;
  private boolean myDropAllowed;

  public GridDropLocation(final boolean dropAllowed) {
    myDropAllowed = dropAllowed;
  }

  public GridDropLocation(final RadContainer container, final int row, final int column) {
    myContainer = container;
    myRow = row;
    myColumn = column;
    myDropAllowed = true;
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

  public void rejectDrop() {
    myDropAllowed = false;
  }

  public boolean canDrop(final ComponentDragObject dragObject) {
    if (!myDropAllowed) {
      LOG.debug("drop not allowed");
      return false;
    }

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

      final RadComponent componentInRect = myContainer.findComponentInRect(myRow + relativeRow, myColumn + relativeCol, rowSpan, colSpan);
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

  public void placeFeedback(FeedbackLayer feedbackLayer, ComponentDragObject dragObject) {
    Rectangle feedbackRect = null;
    if (getContainer().getLayoutManager().isGrid()) {
      feedbackRect = getGridFeedbackRect(dragObject);
    }
    if (feedbackRect != null) {
      final JComponent component = getContainer().getDelegee();
      feedbackLayer.putFeedback(component, feedbackRect, getContainer().getDisplayName() + " (" + myRow + "," + myColumn + ")");
    }
    else {
      feedbackLayer.removeFeedback();
    }
  }

  @Nullable
  protected Rectangle getGridFeedbackCellRect(ComponentDragObject dragObject) {
    if (dragObject.getComponentCount() == 0) {
      LOG.debug("no feedback rect because component count=0");
      return null;
    }

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

    if (firstRow < 0 || firstCol < 0 ||
        lastRow >= getContainer().getGridRowCount() || lastCol >= getContainer().getGridColumnCount()) {
      LOG.debug("no feedback rect because insert range is outside grid: firstRow=" + firstRow +
                ", firstCol=" + firstCol + ", lastRow=" + lastRow + ", lastCol=" + lastCol);
      return null;
    }
    return new Rectangle(firstCol, firstRow, lastCol-firstCol, lastRow-firstRow);
  }

  @Nullable
  protected Rectangle getGridFeedbackRect(ComponentDragObject dragObject) {
    Rectangle cellRect = getGridFeedbackCellRect(dragObject);
    if (cellRect == null) return null;
    return getContainer().getGridLayoutManager().getGridCellRangeRect(getContainer(), cellRect.y, cellRect.x,
                                                                      cellRect.y+cellRect.height, cellRect.x+cellRect.width);
  }

  public void processDrop(final GuiEditor editor,
                          final RadComponent[] components,
                          final GridConstraints[] constraintsToAdjust,
                          final ComponentDragObject dragObject) {
    dropIntoGrid(myContainer, components, myRow, myColumn, dragObject);
  }

  @Nullable
  public DropLocation getAdjacentLocation(Direction direction) {
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
      assert relativeRow + rowSpan <= container.getGridRowCount();
      assert relativeCol + colSpan <= container.getGridColumnCount();

      RadComponent old = container.findComponentInRect(row + relativeRow, column + relativeCol, rowSpan, colSpan);
      if (old != null) {
        LOG.assertTrue(false,
                       "Drop rectangle not empty: (" + (row + relativeRow) + ", " + (column + relativeCol)
                       + ", " + rowSpan + ", " + colSpan + "), component ID=" + old.getId());
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
