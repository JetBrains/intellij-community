/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.designSurface;

import com.intellij.uiDesigner.GridChangeUtil;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author yole
 */
public class GridInsertLocation extends GridDropLocation {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.designSurface.GridInsertLocation");

  public static final int INSERT_ARROW_SIZE = 3;
  public static final int INSERT_RECT_MIN_SIZE = 15;  // should be larger than the insets increase on Shift

  private GridInsertMode myMode;

  public GridInsertLocation(@NotNull final RadContainer container,
                            final int row,
                            final int column,
                            final GridInsertMode mode) {
    super(container, row, column);
    myMode = mode;
    assert container.getLayoutManager().isGrid();
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
        RadComponent component = getContainer().getGridLayoutManager().getComponentAtGrid(getContainer(), row, col);
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
        RadComponent component = getContainer().getGridLayoutManager().getComponentAtGrid(getContainer(), row, col);
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

    Rectangle feedbackRect = getGridFeedbackRect(dragObject);
    if (feedbackRect == null) {
      feedbackLayer.removeFeedback();
      return;
    }
    Rectangle cellRect = getGridFeedbackCellRect(dragObject);
    assert cellRect != null;

    int[] vGridLines = getContainer().getGridLayoutManager().getVerticalGridLines(getContainer());
    int[] hGridLines = getContainer().getGridLayoutManager().getHorizontalGridLines(getContainer());

    FeedbackPainter painter = (myMode == GridInsertMode.ColumnBefore ||
                               myMode == GridInsertMode.ColumnAfter)
                              ? VertInsertFeedbackPainter.INSTANCE
                              : HorzInsertFeedbackPainter.INSTANCE;
    Rectangle rc;

    Rectangle rcFeedback = null;
    if (dragObject.getComponentCount() == 1 && myMode != GridInsertMode.InCell) {
      RadComponent component = getContainer().getGridLayoutManager().getComponentAtGrid(getContainer(), insertRow, insertCol);
      if (component != null) {
        Rectangle bounds = component.getBounds();
        int cellWidth = vGridLines [insertCol+1] - vGridLines [insertCol];
        int cellHeight = hGridLines [insertRow+1] - hGridLines [insertRow];
        bounds.translate(-vGridLines [insertCol], -hGridLines [insertRow]);

        int spaceToRight = vGridLines [insertCol+1] - vGridLines [insertCol] - (bounds.x + bounds.width);
        int spaceBelow = hGridLines [insertRow+1] - hGridLines [insertRow] - (bounds.y + bounds.height);
        if (myMode == GridInsertMode.RowBefore && bounds.y > INSERT_RECT_MIN_SIZE) {
          rcFeedback = new Rectangle(0, 0, cellWidth, bounds.y);
        }
        else if (myMode == GridInsertMode.RowAfter && spaceBelow > INSERT_RECT_MIN_SIZE) {
          rcFeedback = new Rectangle(0, bounds.y + bounds.height, cellWidth, spaceBelow);
        }
        else if (myMode == GridInsertMode.ColumnBefore && bounds.x > INSERT_RECT_MIN_SIZE) {
          rcFeedback = new Rectangle(0, 0, bounds.x, cellHeight);
        }
        else if (myMode == GridInsertMode.ColumnAfter && spaceToRight > INSERT_RECT_MIN_SIZE) {
          rcFeedback = new Rectangle(bounds.x + bounds.width, 0, spaceToRight, cellHeight);
        }

        if (rcFeedback != null) {
          rcFeedback.translate(vGridLines [insertCol], hGridLines [insertRow]);
          feedbackLayer.putFeedback(getContainer().getDelegee(), rcFeedback);
          return;
        }
      }
    }

    rc = getInsertFeedbackPosition(myMode, getContainer(), cellRect, feedbackRect);
    if (myMode == GridInsertMode.InCell) {
      painter = null;
    }
    feedbackLayer.putFeedback(getContainer().getDelegee(), rc, painter);
  }

  public static Rectangle getInsertFeedbackPosition(final GridInsertMode mode, final RadContainer container, final Rectangle cellRect,
                                                    final Rectangle feedbackRect) {
    int[] vGridLines = container.getGridLayoutManager().getVerticalGridLines(container);
    int[] hGridLines = container.getGridLayoutManager().getHorizontalGridLines(container);

    int w=4;
    //noinspection EnumSwitchStatementWhichMissesCases
    switch (mode) {
      case ColumnBefore:
        return new Rectangle(vGridLines [cellRect.x] - w, feedbackRect.y - INSERT_ARROW_SIZE,
                             2 * w, feedbackRect.height + 2 * INSERT_ARROW_SIZE);

      case ColumnAfter:
        return new Rectangle(vGridLines [cellRect.x + cellRect.width+1] - w, feedbackRect.y - INSERT_ARROW_SIZE,
                             2 * w, feedbackRect.height + 2 * INSERT_ARROW_SIZE);

      case RowBefore:
        return new Rectangle(feedbackRect.x - INSERT_ARROW_SIZE, hGridLines [cellRect.y] - w,
                             feedbackRect.width + 2 * INSERT_ARROW_SIZE, 2 * w);

      case RowAfter:
        return new Rectangle(feedbackRect.x - INSERT_ARROW_SIZE, hGridLines [cellRect.y+cellRect.height+1] - w,
                             feedbackRect.width + 2 * INSERT_ARROW_SIZE, 2 * w);

      default:
        return feedbackRect;
    }
  }


  @Override
  public void processDrop(final GuiEditor editor,
                          final RadComponent[] components,
                          @Nullable final GridConstraints[] constraintsToAdjust,
                          final ComponentDragObject dragObject) {
    int row = getRow();
    int col = getColumn();
    boolean canHGrow = (dragObject.getHSizePolicy() & GridConstraints.SIZEPOLICY_WANT_GROW) != 0;
    boolean canVGrow = (dragObject.getHSizePolicy() & GridConstraints.SIZEPOLICY_WANT_GROW) != 0;
    int insertedCells;
    RadContainer container = getContainer();
    //noinspection EnumSwitchStatementWhichMissesCases
    switch(myMode) {
      case RowBefore:
        insertedCells = container.getGridLayoutManager().insertGridCells(container, row, true, true, canVGrow);
        checkAdjustConstraints(constraintsToAdjust, true, row, insertedCells);
        break;

      case RowAfter:
        insertedCells = container.getGridLayoutManager().insertGridCells(container, row, true, false, canVGrow);
        row += insertedCells;
        checkAdjustConstraints(constraintsToAdjust, true, row, insertedCells);
        break;

      case ColumnBefore:
        insertedCells = container.getGridLayoutManager().insertGridCells(container, col, false, true, canHGrow);
        checkAdjustConstraints(constraintsToAdjust, false, col, insertedCells);
        break;

      case ColumnAfter:
        insertedCells = container.getGridLayoutManager().insertGridCells(container, col, false, false, canHGrow);
        col += insertedCells;
        checkAdjustConstraints(constraintsToAdjust, false, col, insertedCells);
        break;
    }
    if (components.length > 0) {
      dropIntoGrid(container, components, row, col, dragObject);
    }
  }

  private static void checkAdjustConstraints(@Nullable final GridConstraints[] constraintsToAdjust,
                                             final boolean isRow,
                                             final int index, final int count) {
    if (constraintsToAdjust != null) {
      for(GridConstraints constraints: constraintsToAdjust) {
        GridChangeUtil.adjustConstraintsOnInsert(constraints, isRow, index, count);
      }
    }
  }


  @NonNls @Override public String toString() {
    return "GridInsertLocation(" + myMode.toString() + ", row=" + getRow() + ", col=" + getColumn() + ")";
  }

  @Override @Nullable
  public DropLocation getAdjacentLocation(Direction direction) {
    if (isRowInsert()) {
      if (direction == Direction.RIGHT) {
        if (getColumn() < myContainer.getGridColumnCount()-1) {
          return new GridInsertLocation(myContainer, getRow(), getColumn()+1, getMode());
        }
        return new GridInsertLocation(myContainer, getRow(), getColumn(), GridInsertMode.ColumnAfter);
      }
      if (direction == Direction.LEFT) {
        if (getColumn() > 0) {
          return new GridInsertLocation(myContainer, getRow(), getColumn()-1, getMode());
        }
        return new GridInsertLocation(myContainer, getRow(), getColumn(), GridInsertMode.ColumnBefore);
      }
      if (direction == Direction.DOWN || direction == Direction.UP) {
        int adjRow = (myMode == GridInsertMode.RowAfter) ? getRow() : getRow()-1;
        if (direction == Direction.DOWN && adjRow+1 < myContainer.getGridRowCount()) {
          return new GridDropLocation(myContainer, adjRow+1, getColumn());
        }
        if (direction == Direction.UP && adjRow >= 0) {
          return new GridDropLocation(myContainer, adjRow, getColumn());
        }
        return getLocationAtParent(direction);
      }
    }
    else {
      if (direction == Direction.DOWN) {
        if (getRow() < myContainer.getGridRowCount()-1) {
          return new GridInsertLocation(myContainer, getRow()+1, getColumn(), getMode());
        }
        return new GridInsertLocation(myContainer, getRow(), getColumn(), GridInsertMode.RowAfter);
      }
      if (direction == Direction.UP) {
        if (getRow() > 0) {
          return new GridInsertLocation(myContainer, getRow()-1, getColumn(), getMode());
        }
        return new GridInsertLocation(myContainer, getRow(), getColumn(), GridInsertMode.RowBefore);
      }
      if (direction == Direction.LEFT || direction == Direction.RIGHT) {
        int adjCol = (myMode == GridInsertMode.ColumnAfter) ? getColumn() : getColumn()-1;
        if (direction == Direction.RIGHT && adjCol+1 < myContainer.getGridColumnCount()) {
          return new GridDropLocation(myContainer, getRow(), adjCol+1);
        }
        if (direction == Direction.LEFT && adjCol >= 0) {
          return new GridDropLocation(myContainer, getRow(), adjCol);
        }
        return getLocationAtParent(direction);
      }
    }
    return null;
  }

  private DropLocation getLocationAtParent(final Direction direction) {
    final RadContainer parent = myContainer.getParent();
    if (parent.getLayoutManager().isGrid()) {
      final GridConstraints c = myContainer.getConstraints();
      switch(direction) {
        case LEFT: return new GridInsertLocation(parent, c.getRow(), c.getColumn(), GridInsertMode.ColumnBefore);
        case RIGHT: return new GridInsertLocation(parent, c.getRow(), c.getColumn()+c.getColSpan()-1, GridInsertMode.ColumnAfter);
        case UP: return new GridInsertLocation(parent, c.getRow(), c.getColumn(), GridInsertMode.RowBefore);
        case DOWN: return new GridInsertLocation(parent, c.getRow()+c.getRowSpan()-1, c.getColumn(), GridInsertMode.RowAfter);
      }
    }
    return null;
  }
}
