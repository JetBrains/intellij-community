package com.intellij.uiDesigner.designSurface;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import org.jetbrains.annotations.NonNls;

import java.awt.Rectangle;

/**
 * @author yole
 */
public class GridDropLocation implements DropLocation {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.designSurface.GridDropLocation");

  protected RadContainer myContainer;
  protected int myRow;
  private int myColumn;
  private boolean myDropAllowed;

  public GridDropLocation(final boolean dropAllowed) {
    myDropAllowed = dropAllowed;
  }

  public GridDropLocation(final RadContainer container,
                          final int row,
                          final int column) {
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

    final GridLayoutManager gridLayout = (GridLayoutManager)myContainer.getLayout();

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
          myRow + relativeRow + rowSpan > gridLayout.getRowCount() ||
          myColumn + relativeCol + colSpan > gridLayout.getColumnCount()) {
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
    if (getContainer().isGrid()) {
      feedbackRect = getGridFeedbackRect(dragObject);
    }
    if (feedbackRect != null) {
      feedbackLayer.putFeedback(getContainer().getDelegee(), feedbackRect);
    }
    else {
      feedbackLayer.removeFeedback();
    }
  }

  protected Rectangle getGridFeedbackRect(ComponentDragObject dragObject) {
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

    final GridLayoutManager layoutManager = (GridLayoutManager) getContainer().getLayout();

    if (firstRow < 0 || firstCol < 0 ||
        lastRow >= layoutManager.getRowCount() || lastCol >= layoutManager.getColumnCount()) {
      LOG.debug("no feedback rect because insert range is outside grid: firstRow=" + firstRow +
                ", firstCol=" + firstCol + ", lastRow=" + lastRow + ", lastCol=" + lastCol);
      return null;
    }

    int[] xs = layoutManager.getXs();
    int[] widths = layoutManager.getWidths();
    int[] ys = layoutManager.getYs();
    int[] heights = layoutManager.getHeights();
    return new Rectangle(xs [firstCol],
                         ys [firstRow],
                         xs [lastCol] + widths [lastCol] - xs [firstCol],
                         ys [lastRow] + heights [lastRow] - ys [firstRow]);
  }

  public void processDrop(final GuiEditor editor,
                          final RadComponent[] components,
                          final GridConstraints[] constraintsToAdjust,
                          final ComponentDragObject dragObject) {
    myContainer.dropIntoGrid(components, myRow, myColumn, dragObject);
  }

  @NonNls @Override public String toString() {
    return "GridDropLocation(row=" + myRow + ",col=" + myColumn + ")";
  }
}
