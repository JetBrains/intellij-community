/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.designSurface;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.GridChangeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
class GridInsertLocation extends GridLocation {
  private static final int INSERT_ARROW_SIZE = 3;
  public static final int INSERT_RECT_MIN_SIZE = 15;  // should be larger than the insets increase on Shift

  private FeedbackPainter myHorzInsertFeedbackPainter = new HorzInsertFeedbackPainter();
  private FeedbackPainter myVertInsertFeedbackPainter = new VertInsertFeedbackPainter();

  public GridInsertLocation(@NotNull final RadContainer container,
                            final int row,
                            final int column,
                            final Point targetPoint,
                            final Rectangle cellRect,
                            final GridInsertMode mode) {
    super(container, row, column, targetPoint, cellRect, mode);
    assert container.isGrid();
  }


  public boolean isColumnInsert() {
    return getMode() == GridInsertMode.ColumnAfter || getMode() == GridInsertMode.ColumnBefore;
  }

  public boolean isRowInsert() {
    return getMode() == GridInsertMode.RowAfter || getMode() == GridInsertMode.RowBefore;
  }

  public boolean isInsert() {
    return isColumnInsert() || isRowInsert();
  }

  @Override public boolean canDrop(final int componentCount) {
    final GridLayoutManager grid = ((GridLayoutManager) getContainer().getLayout());
    if (isInsertInsideComponent()) {
      return false;
    }

    if (isColumnInsert()) {
      return componentCount == 1;
    }
    return getColumn() + componentCount - 1 < grid.getColumnCount();
  }

  private boolean isInsertInsideComponent() {
    if (isColumnInsert()) {
      int endColumn = (getMode() == GridInsertMode.ColumnAfter)
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
      int endRow = (getMode() == GridInsertMode.RowAfter)
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

  @Override public void placeFeedback(GuiEditor editor, int componentCount) {
    final int insertCol = getColumn();
    final int insertRow = getRow();
    final GridInsertMode insertMode = getMode();

    Rectangle cellRect = getGridFeedbackRect(componentCount);

    FeedbackPainter painter = (insertMode == GridInsertMode.ColumnBefore ||
                               insertMode == GridInsertMode.ColumnAfter)
                              ? myVertInsertFeedbackPainter
                              : myHorzInsertFeedbackPainter;
    Rectangle rc;

    Rectangle rcFeedback = null;
    if (componentCount == 1 && insertMode != GridInsertMode.InCell) {
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
          rcFeedback = SwingUtilities.convertRectangle(getContainer().getDelegee(),
                                                       rcFeedback,
                                                       editor.getActiveDecorationLayer());
          editor.getActiveDecorationLayer().putFeedback(rcFeedback);
          return;
        }
      }
    }

    cellRect = SwingUtilities.convertRectangle(getContainer().getDelegee(),
                                               cellRect,
                                               editor.getActiveDecorationLayer());
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
    editor.getActiveDecorationLayer().putFeedback(rc, painter);
  }


  @Override
  public void processDrop(final GuiEditor editor,
                          final RadComponent[] components,
                          final GridConstraints[] constraintsToAdjust,
                          final int[] dx,
                          final int[] dy) {
    int row = getRow();
    int col = getColumn();
    RadContainer container = getContainer();
    //noinspection EnumSwitchStatementWhichMissesCases
    switch(getMode()) {
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
    container.dropIntoGrid(components, row, col);
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
