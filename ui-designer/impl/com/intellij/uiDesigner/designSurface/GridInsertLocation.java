/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.designSurface;

import com.intellij.uiDesigner.GridChangeUtil;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import java.awt.*;

/**
 * @author yole
 */
class GridInsertLocation extends GridDropLocation {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.designSurface.GridInsertLocation");

  private static final int INSERT_ARROW_SIZE = 3;
  public static final int INSERT_RECT_MIN_SIZE = 15;  // should be larger than the insets increase on Shift

  private FeedbackPainter myHorzInsertFeedbackPainter = new HorzInsertFeedbackPainter();
  private FeedbackPainter myVertInsertFeedbackPainter = new VertInsertFeedbackPainter();
  private GridInsertMode myMode;

  public GridInsertLocation(@NotNull final RadContainer container,
                            final int row,
                            final int column,
                            final Point targetPoint,
                            final Rectangle cellRect,
                            final GridInsertMode mode) {
    super(container, row, column, targetPoint, cellRect);
    myMode = mode;
    assert container.isGrid();
  }

  public GridInsertMode getMode() {
    return myMode;
  }

  private boolean isColumnInsert() {
    return myMode == GridInsertMode.ColumnAfter || myMode == GridInsertMode.ColumnBefore;
  }

  private boolean isRowInsert() {
    return myMode == GridInsertMode.RowAfter || myMode == GridInsertMode.RowBefore;
  }

  public boolean isInsert() {
    return isColumnInsert() || isRowInsert();
  }

  @Override public boolean canDrop(ComponentDragObject dragObject) {
    if (isInsertInsideComponent()) {
      LOG.debug("GridInsertLocation.canDrop()=false because insert inside component");
      return false;
    }

    if (isColumnInsert() && !isSameCell(dragObject, false)) {
      LOG.debug("GridInsertLocation.canDrop()=false because column insert and columns are different");
      return false;
    }
    if (isRowInsert() && !isSameCell(dragObject, true)) {
      LOG.debug("GridInsertLocation.canDrop()=false because column insert and columns are different");
      return false;
    }

    return getGridFeedbackRect(dragObject) != null;
  }

  private static boolean isSameCell(final ComponentDragObject dragObject, boolean isRow) {
    if (dragObject.getComponentCount() == 0) {
      return true;
    }
    int cell = isRow ? dragObject.getRelativeRow(0) : dragObject.getRelativeCol(0);
    for(int i=1; i<dragObject.getComponentCount(); i++) {
      int cell2 = isRow ? dragObject.getRelativeRow(i) : dragObject.getRelativeCol(i);
      if (cell2 != cell) {
        return false;
      }
    }
    return true;
  }

  private boolean isInsertInsideComponent() {
    if (isColumnInsert()) {
      int endColumn = (myMode == GridInsertMode.ColumnAfter)
                      ? getColumn()+1 : getColumn();
      int row = getRow();
      for(int col = 0; col<endColumn; col++) {
        RadComponent component = getContainer().getComponentAtGrid(row, col);
        if (component != null) {
          GridConstraints constraints = component.getConstraints();
          if (constraints.getColumn() + constraints.getColSpan() > endColumn &&
              constraints.getColSpan() > 1) {
            return true;
          }
        }
      }
      return false;
    }
    else if (isRowInsert()) {
      int endRow = (myMode == GridInsertMode.RowAfter)
                   ? getRow()+1 : getRow();
      int col = getColumn();
      for(int row = 0; row<endRow; row++) {
        RadComponent component = getContainer().getComponentAtGrid(row, col);
        if (component != null) {
          GridConstraints constraints = component.getConstraints();
          if (constraints.getRow() + constraints.getRowSpan() > endRow &&
              constraints.getRowSpan() > 1) {
            return true;
          }
        }
      }
      return false;
    }
    return false;
  }

  @Override public void placeFeedback(FeedbackLayer feedbackLayer, ComponentDragObject dragObject) {
    final int insertCol = getColumn();
    final int insertRow = getRow();
    final GridInsertMode insertMode = myMode;

    Rectangle cellRect = getGridFeedbackRect(dragObject);
    if (cellRect == null) {
      feedbackLayer.removeFeedback();
      return;
    }

    FeedbackPainter painter = (insertMode == GridInsertMode.ColumnBefore ||
                               insertMode == GridInsertMode.ColumnAfter)
                              ? myVertInsertFeedbackPainter
                              : myHorzInsertFeedbackPainter;
    Rectangle rc;

    Rectangle rcFeedback = null;
    if (dragObject.getComponentCount() == 1 && insertMode != GridInsertMode.InCell) {
      RadComponent component = getContainer().getComponentAtGrid(insertRow, insertCol);
      if (component != null) {
        Rectangle bounds = component.getBounds();
        final GridLayoutManager layoutManager = (GridLayoutManager) getContainer().getLayout();
        int[] vGridLines = layoutManager.getVerticalGridLines();
        int[] hGridLines = layoutManager.getHorizontalGridLines();
        int cellWidth = vGridLines [insertCol+1] - vGridLines [insertCol];
        int cellHeight = hGridLines [insertRow+1] - hGridLines [insertRow];
        bounds.translate(-vGridLines [insertCol], -hGridLines [insertRow]);

        int spaceToRight = vGridLines [insertCol+1] - vGridLines [insertCol] - (bounds.x + bounds.width);
        int spaceBelow = hGridLines [insertRow+1] - hGridLines [insertRow] - (bounds.y + bounds.height);
        if (insertMode == GridInsertMode.RowBefore && bounds.y > INSERT_RECT_MIN_SIZE) {
          rcFeedback = new Rectangle(0, 0, cellWidth, bounds.y);
        }
        else if (insertMode == GridInsertMode.RowAfter && spaceBelow > INSERT_RECT_MIN_SIZE) {
          rcFeedback = new Rectangle(0, bounds.y + bounds.height, cellWidth, spaceBelow);
        }
        else if (insertMode == GridInsertMode.ColumnBefore && bounds.x > INSERT_RECT_MIN_SIZE) {
          rcFeedback = new Rectangle(0, 0, bounds.x, cellHeight);
        }
        else if (insertMode == GridInsertMode.ColumnAfter && spaceToRight > INSERT_RECT_MIN_SIZE) {
          rcFeedback = new Rectangle(bounds.x + bounds.width, 0, spaceToRight, cellHeight);
        }

        if (rcFeedback != null) {
          rcFeedback.translate(vGridLines [insertCol], hGridLines [insertRow]);
          feedbackLayer.putFeedback(getContainer().getDelegee(), rcFeedback);
          return;
        }
      }
    }

    int w=4;
    //noinspection EnumSwitchStatementWhichMissesCases
    switch (insertMode) {
      case ColumnBefore:
        rc = new Rectangle(cellRect.x - w, cellRect.y - INSERT_ARROW_SIZE,
                           2 * w, cellRect.height + 2 * INSERT_ARROW_SIZE);
        break;

      case ColumnAfter:
        rc = new Rectangle((int)cellRect.getMaxX() - w, (int)cellRect.getMinY() - INSERT_ARROW_SIZE,
                           2 * w, (int)cellRect.getHeight() + 2 * INSERT_ARROW_SIZE);
        break;

      case RowBefore:
        rc = new Rectangle((int)cellRect.getMinX() - INSERT_ARROW_SIZE, (int)cellRect.getMinY() - w,
                           (int)cellRect.getWidth() + 2 * INSERT_ARROW_SIZE, 2 * w);
        break;

      case RowAfter:
        rc = new Rectangle((int)cellRect.getMinX() - INSERT_ARROW_SIZE, (int)cellRect.getMaxY() - w,
                           (int)cellRect.getWidth() + 2 * INSERT_ARROW_SIZE, 2 * w);
        break;

      default:
        rc = cellRect;
        painter = null;
    }
    feedbackLayer.putFeedback(getContainer().getDelegee(), rc, painter);
  }


  @Override
  public void processDrop(final GuiEditor editor,
                          final RadComponent[] components,
                          final GridConstraints[] constraintsToAdjust,
                          final ComponentDragObject dragObject) {
    int row = getRow();
    int col = getColumn();
    RadContainer container = getContainer();
    //noinspection EnumSwitchStatementWhichMissesCases
    switch(myMode) {
      case RowBefore:
        GridChangeUtil.insertRowBefore(container, row);
        checkAdjustConstraints(constraintsToAdjust, true, row);
        break;

      case RowAfter:
        GridChangeUtil.insertRowAfter(container, row);
        row++;
        checkAdjustConstraints(constraintsToAdjust, true, row);
        break;

      case ColumnBefore:
        GridChangeUtil.insertColumnBefore(container, col);
        checkAdjustConstraints(constraintsToAdjust, false, col);
        break;

      case ColumnAfter:
        GridChangeUtil.insertColumnAfter(container, col);
        col++;
        checkAdjustConstraints(constraintsToAdjust, false, col);
        break;
    }
    container.dropIntoGrid(components, row, col, dragObject);
  }

  private static void checkAdjustConstraints(final GridConstraints[] constraintsToAdjust,
                                             final boolean isRow,
                                             final int index) {
    if (constraintsToAdjust != null) {
      for(GridConstraints constraints: constraintsToAdjust) {
        GridChangeUtil.adjustConstraintsOnInsert(constraints, isRow, index);
      }
    }
  }


  @NonNls @Override public String toString() {
    return "GridInsertLocation(" + myMode.toString() + ", row=" + getRow() + ", col=" + getColumn() + ")";
  }

  private static class HorzInsertFeedbackPainter implements FeedbackPainter {
    public void paintFeedback(Graphics2D g2d, Rectangle rc) {
      g2d.setColor(Color.BLUE);
      g2d.setStroke(new BasicStroke(1.5f));
      int midY = (int)rc.getCenterY();
      int right = rc.x + rc.width - 1;
      int bottom = rc.y + rc.height - 1;
      g2d.drawLine(rc.x, rc.y, INSERT_ARROW_SIZE, midY);
      g2d.drawLine(rc.x, bottom, INSERT_ARROW_SIZE, midY);
      g2d.drawLine(INSERT_ARROW_SIZE, midY,
                   right - INSERT_ARROW_SIZE, midY);
      g2d.drawLine(right, rc.y,
                   rc.x+rc.width-INSERT_ARROW_SIZE, midY);
      g2d.drawLine(right, bottom,
                   right-INSERT_ARROW_SIZE, midY);
    }
  }

  private static class VertInsertFeedbackPainter implements FeedbackPainter {
    public void paintFeedback(Graphics2D g2d, Rectangle rc) {
      g2d.setColor(Color.BLUE);
      g2d.setStroke(new BasicStroke(1.5f));
      int right = rc.x + rc.width - 1;
      int bottom = rc.y + rc.height - 1;
      int midX = (int) rc.getCenterX();
      g2d.drawLine(rc.x, rc.y, midX, rc.y+INSERT_ARROW_SIZE);
      g2d.drawLine(right, rc.y, midX, rc.y+INSERT_ARROW_SIZE);
      g2d.drawLine(midX, rc.y+INSERT_ARROW_SIZE,
                   midX, bottom-INSERT_ARROW_SIZE);
      g2d.drawLine(rc.x, bottom, midX, bottom-INSERT_ARROW_SIZE);
      g2d.drawLine(right, bottom, midX, bottom-INSERT_ARROW_SIZE);
    }
  }
}
