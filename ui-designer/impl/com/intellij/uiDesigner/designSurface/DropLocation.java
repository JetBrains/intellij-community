package com.intellij.uiDesigner.designSurface;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;

import java.awt.*;

/**
 * @author yole
 */
class DropLocation {
  protected RadContainer myContainer;
  private int myRow;
  private int myColumn;
  protected Point myTargetPoint;
  protected Rectangle myCellRect;
  private boolean myDropAllowed;

  public DropLocation(final boolean dropAllowed) {
    myDropAllowed = dropAllowed;
  }


  public DropLocation(final RadContainer container, final Point targetPoint, final boolean dropAllowed) {
    myContainer = container;
    myTargetPoint = targetPoint;
    myDropAllowed = dropAllowed;
  }

  public DropLocation(final RadContainer container,
                      final int row,
                      final int column,
                      final Point targetPoint,
                      final Rectangle cellRect,
                      final boolean dropAllowed) {
    myContainer = container;
    myRow = row;
    myColumn = column;
    myTargetPoint = targetPoint;
    myCellRect = cellRect;
    myDropAllowed = dropAllowed;
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
      return false;
    }
    return myContainer.canDrop(myTargetPoint, dragObject);
  }

  public void placeFeedback(GuiEditor editor, ComponentDragObject dragObject) {
    int componentCount = dragObject.getComponentCount();
    Rectangle feedbackRect;
    if (getContainer().isGrid()) {
      feedbackRect = getGridFeedbackRect(componentCount);
    }
    else {
      feedbackRect = getContainer().getDropFeedbackRectangle(myTargetPoint, componentCount);
    }
    if (feedbackRect != null) {
      editor.getActiveDecorationLayer().putFeedback(getContainer().getDelegee(), feedbackRect);
    }
    else {
      editor.getActiveDecorationLayer().removeFeedback();
    }

  }

  protected Rectangle getGridFeedbackRect(final int componentCount) {
    if (componentCount == 0) {
      return null;
    }
    if (componentCount == 1) {
      return getCellRect();
    }
    int insertCol = getColumn();
    int lastCol = insertCol + componentCount - 1;
    Rectangle cellRect = getCellRect();
    final GridLayoutManager layoutManager = (GridLayoutManager) getContainer().getLayout();
    if (lastCol >= layoutManager.getColumnCount()) {
      lastCol = layoutManager.getColumnCount()-1;
    }
    int[] xs = layoutManager.getXs();
    int[] widths = layoutManager.getWidths();
    return new Rectangle(xs [insertCol], cellRect.y,
                         xs [lastCol] + widths [lastCol] - xs [insertCol], cellRect.height);
  }

  public void processDrop(final GuiEditor editor,
                          final RadComponent[] components,
                          final GridConstraints[] originalConstraints,
                          final int[] dx,
                          final int[] dy) {
    myContainer.drop(myTargetPoint, components, dx, dy);
  }
}
