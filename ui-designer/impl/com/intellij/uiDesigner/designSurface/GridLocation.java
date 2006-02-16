package com.intellij.uiDesigner.designSurface;

import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.GridConstraints;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
class GridLocation {
  private RadContainer myContainer;
  private int myRow;
  private int myColumn;
  private Point myTargetPoint;
  private Rectangle myCellRect;
  private GridInsertMode myMode;

  public GridLocation(final GridInsertMode mode) {
    myMode = mode;
  }


  public GridLocation(final RadContainer container, final Point targetPoint, final GridInsertMode mode) {
    myContainer = container;
    myTargetPoint = targetPoint;
    myMode = mode;
  }

  public GridLocation(final RadContainer container,
                      final int row,
                      final int column,
                      final Point targetPoint,
                      final Rectangle cellRect,
                      final GridInsertMode mode) {
    myContainer = container;
    myRow = row;
    myColumn = column;
    myTargetPoint = targetPoint;
    myCellRect = cellRect;
    myMode = mode;
  }

  public GridInsertMode getMode() {
    return myMode;
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
    myMode = GridInsertMode.NoDrop;
  }

  public boolean canDrop(final ComponentDragObject dragObject) {
    if (myMode == GridInsertMode.NoDrop) {
      return false;
    }
    return myContainer.canDrop(myTargetPoint, dragObject.getComponentCount());
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
      final Rectangle rc = SwingUtilities.convertRectangle(getContainer().getDelegee(),
                                                           feedbackRect,
                                                           editor.getActiveDecorationLayer());
      editor.getActiveDecorationLayer().putFeedback(rc);
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
