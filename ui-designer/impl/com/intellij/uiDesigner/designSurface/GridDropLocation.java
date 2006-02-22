package com.intellij.uiDesigner.designSurface;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NonNls;

import java.awt.*;

/**
 * @author yole
 */
class GridDropLocation implements DropLocation {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.designSurface.GridDropLocation");

  protected RadContainer myContainer;
  protected int myRow;
  private int myColumn;
  protected Point myTargetPoint;
  protected Rectangle myCellRect;
  private boolean myDropAllowed;

  public GridDropLocation(final boolean dropAllowed) {
    myDropAllowed = dropAllowed;
  }


  public GridDropLocation(final RadContainer container, final Point targetPoint, final boolean dropAllowed) {
    myContainer = container;
    myTargetPoint = targetPoint;
    myDropAllowed = dropAllowed;
  }

  public GridDropLocation(final RadContainer container,
                          final int row,
                          final int column,
                          final Point targetPoint,
                          final Rectangle cellRect) {
    myContainer = container;
    myRow = row;
    myColumn = column;
    myTargetPoint = targetPoint;
    myCellRect = cellRect;
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

  public Rectangle getCellRect() {
    return myCellRect;
  }

  public Point getTargetPoint() {
    return myTargetPoint;
  }

  public void rejectDrop() {
    myDropAllowed = false;
  }

  public boolean canDrop(final ComponentDragObject dragObject) {
    if (!myDropAllowed) {
      LOG.debug("drop not allowed");
      return false;
    }
    return myContainer.canDrop(myTargetPoint, dragObject);
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
    myContainer.drop(myTargetPoint, components, dragObject);
  }

  @NonNls @Override public String toString() {
    return "GridDropLocation(row=" + myRow + ",col=" + myColumn + ")";
  }
}
