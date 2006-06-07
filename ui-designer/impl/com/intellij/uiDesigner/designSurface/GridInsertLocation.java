/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.designSurface;

import com.intellij.uiDesigner.GridChangeUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.radComponents.RadAbstractGridLayoutManager;
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

  public GridInsertLocation normalize() {
    final RadAbstractGridLayoutManager gridManager = myContainer.getGridLayoutManager();

    if (myMode == GridInsertMode.RowBefore && myRow > 0) {
      myMode = GridInsertMode.RowAfter;
      myRow--;
    }
    else if (myMode == GridInsertMode.ColumnBefore && myColumn > 0) {
      myMode = GridInsertMode.ColumnAfter;
      myColumn--;
    }

    if (myMode == GridInsertMode.RowAfter && gridManager.isGapCell(myContainer, true, myRow)) {
      myRow--;
    }
    else if (myMode == GridInsertMode.RowBefore && gridManager.isGapCell(myContainer, true, myRow)) {
      myRow++;
    }
    else if (myMode == GridInsertMode.ColumnAfter && gridManager.isGapCell(myContainer, false, myColumn)) {
      myColumn--;
    }
    else if (myMode == GridInsertMode.ColumnBefore && gridManager.isGapCell(myContainer, false, myColumn)) {
      myColumn++;
    }

    return this;
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

    final RadAbstractGridLayoutManager layoutManager = getContainer().getGridLayoutManager();
    int[] vGridLines = layoutManager.getVerticalGridLines(getContainer());
    int[] hGridLines = layoutManager.getHorizontalGridLines(getContainer());

    FeedbackPainter painter = (myMode == GridInsertMode.ColumnBefore ||
                               myMode == GridInsertMode.ColumnAfter)
                              ? VertInsertFeedbackPainter.INSTANCE
                              : HorzInsertFeedbackPainter.INSTANCE;
    Rectangle rc;

    Rectangle rcFeedback = null;
    if (dragObject.getComponentCount() == 1) {
      int cellWidth = vGridLines [insertCol+1] - vGridLines [insertCol];
      int cellHeight = hGridLines [insertRow+1] - hGridLines [insertRow];
      RadComponent component = layoutManager.getComponentAtGrid(getContainer(), insertRow, insertCol);
      if (component != null) {
        Rectangle bounds = component.getBounds();
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
        }
      }

      if (rcFeedback == null) {
        if (insertCol == layoutManager.getGridColumnCount(getContainer())-1 && myMode == GridInsertMode.ColumnAfter) {
          final Dimension initialSize = dragObject.getInitialSize(getContainer().getDelegee());
          int remainingSize = getContainer().getDelegee().getWidth() - vGridLines [vGridLines.length-1];
          if (dragObject.getHSizePolicy() == 0 && remainingSize > initialSize.width) {
            rcFeedback = new Rectangle(vGridLines [vGridLines.length-1], hGridLines [insertRow], initialSize.width, cellHeight);
          }
          else if (remainingSize >= 4) {
            rcFeedback = new Rectangle(vGridLines [vGridLines.length-1], hGridLines [insertRow], remainingSize, cellHeight);
          }
        }
        else if (insertRow == layoutManager.getGridRowCount(getContainer())-1 && myMode == GridInsertMode.RowAfter) {
          final Dimension initialSize = dragObject.getInitialSize(getContainer().getDelegee());
          int remainingSize = getContainer().getDelegee().getHeight() - hGridLines [hGridLines.length-1];
          if (dragObject.getVSizePolicy() == 0 && remainingSize > initialSize.height) {
            rcFeedback = new Rectangle(vGridLines [insertCol], hGridLines [hGridLines.length-1], cellWidth, initialSize.height);
          }
          else if (remainingSize >= 4) {
            rcFeedback = new Rectangle(vGridLines [insertCol], hGridLines [hGridLines.length-1], cellWidth, remainingSize);
          }
        }
      }
    }

    if (rcFeedback != null) {
      feedbackLayer.putFeedback(getContainer().getDelegee(), rcFeedback, getInsertFeedbackTooltip());
      return;
    }

    rc = getInsertFeedbackPosition(myMode, getContainer(), cellRect, feedbackRect);
    feedbackLayer.putFeedback(getContainer().getDelegee(), rc, painter, getInsertFeedbackTooltip());
  }

  private String getInsertFeedbackTooltip() {
    String displayName = getContainer().getDisplayName();
    switch(myMode) {
      case ColumnBefore: return UIDesignerBundle.message("insert.feedback.before.col", displayName, myRow, myColumn);
      case ColumnAfter:  return UIDesignerBundle.message("insert.feedback.after.col", displayName, myRow, myColumn);
      case RowBefore:    return UIDesignerBundle.message("insert.feedback.before.row", displayName, myColumn, myRow);
      case RowAfter:     return UIDesignerBundle.message("insert.feedback.after.row", displayName, myColumn, myRow);
    }
    return null;
  }

  public static Rectangle getInsertFeedbackPosition(final GridInsertMode mode, final RadContainer container, final Rectangle cellRect,
                                                    final Rectangle feedbackRect) {
    final RadAbstractGridLayoutManager manager = container.getGridLayoutManager();
    int[] vGridLines = manager.getVerticalGridLines(container);
    int[] hGridLines = manager.getHorizontalGridLines(container);

    Rectangle rc;
    int w=4;
    //noinspection EnumSwitchStatementWhichMissesCases
    switch (mode) {
      case ColumnBefore:
        rc = new Rectangle(vGridLines [cellRect.x] - w, feedbackRect.y - INSERT_ARROW_SIZE,
                           2 * w, feedbackRect.height + 2 * INSERT_ARROW_SIZE);
        if (cellRect.x > 0 && manager.isGapCell(container, false, cellRect.x-1)) {
          rc.translate(-(vGridLines [cellRect.x] - vGridLines [cellRect.x-1]) / 2, 0);
        }
        break;

      case ColumnAfter:
        rc = new Rectangle(vGridLines [cellRect.x + cellRect.width+1] - w, feedbackRect.y - INSERT_ARROW_SIZE,
                           2 * w, feedbackRect.height + 2 * INSERT_ARROW_SIZE);
        if (cellRect.x < manager.getGridColumnCount(container)-1 && manager.isGapCell(container, false, cellRect.x+1)) {
          rc.translate((vGridLines [cellRect.x+2] - vGridLines [cellRect.x+1]) / 2, 0);
        }
        break;

      case RowBefore:
        rc = new Rectangle(feedbackRect.x - INSERT_ARROW_SIZE, hGridLines [cellRect.y] - w,
                           feedbackRect.width + 2 * INSERT_ARROW_SIZE, 2 * w);
        if (cellRect.y > 0 && manager.isGapCell(container, true, cellRect.y-1)) {
          rc.translate(0, -(hGridLines [cellRect.y] - hGridLines [cellRect.y-1]) / 2);
        }
        break;

      case RowAfter:
        rc = new Rectangle(feedbackRect.x - INSERT_ARROW_SIZE, hGridLines [cellRect.y+cellRect.height+1] - w,
                           feedbackRect.width + 2 * INSERT_ARROW_SIZE, 2 * w);
        if (cellRect.y < manager.getGridRowCount(container)-1 && manager.isGapCell(container, true, cellRect.y+1)) {
          rc.translate(0, (hGridLines [cellRect.y+2] - hGridLines [cellRect.y+1]) / 2);
        }
        break;

      default:
        return feedbackRect;
    }

    return rc;
  }


  @Override
  public void processDrop(final GuiEditor editor,
                          final RadComponent[] components,
                          @Nullable final GridConstraints[] constraintsToAdjust,
                          final ComponentDragObject dragObject) {
    int row = getRow();
    int col = getColumn();
    boolean canHGrow = (dragObject.getHSizePolicy() & GridConstraints.SIZEPOLICY_WANT_GROW) != 0;
    boolean canVGrow = (dragObject.getVSizePolicy() & GridConstraints.SIZEPOLICY_WANT_GROW) != 0;
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
    RadAbstractGridLayoutManager manager = myContainer.getGridLayoutManager();
    if (isRowInsert()) {
      if (direction == Direction.RIGHT) {
        if (getColumn() < myContainer.getGridColumnCount()-1) {
          return new GridInsertLocation(myContainer, getRow(), FormEditingUtil.adjustForGap(myContainer, getColumn()+1, false, 1), getMode());
        }
        return new GridInsertLocation(myContainer, getRow(), getColumn(), GridInsertMode.ColumnAfter);
      }
      if (direction == Direction.LEFT) {
        if (getColumn() > 0) {
          return new GridInsertLocation(myContainer, getRow(), FormEditingUtil.adjustForGap(myContainer, getColumn()-1, false, -1), getMode());
        }
        return new GridInsertLocation(myContainer, getRow(), getColumn(), GridInsertMode.ColumnBefore);
      }
      if (direction == Direction.DOWN || direction == Direction.UP) {
        int adjRow = (myMode == GridInsertMode.RowAfter) ? getRow() : getRow()-1;
        if (direction == Direction.DOWN && adjRow+1 < myContainer.getGridRowCount()) {
          return new GridDropLocation(myContainer, FormEditingUtil.adjustForGap(myContainer, adjRow+1, true, 1), getColumn());
        }
        if (direction == Direction.UP && adjRow >= 0) {
          return new GridDropLocation(myContainer, FormEditingUtil.adjustForGap(myContainer, adjRow, true, -1), getColumn());
        }
        return getLocationAtParent(direction);
      }
    }
    else {
      if (direction == Direction.DOWN) {
        if (getRow() < myContainer.getGridRowCount()-1) {
          return new GridInsertLocation(myContainer, FormEditingUtil.adjustForGap(myContainer, getRow()+1, true, 1), getColumn(), getMode());
        }
        return new GridInsertLocation(myContainer, getRow(), getColumn(), GridInsertMode.RowAfter);
      }
      if (direction == Direction.UP) {
        if (getRow() > 0) {
          return new GridInsertLocation(myContainer, FormEditingUtil.adjustForGap(myContainer, getRow()-1, true, -1), getColumn(), getMode());
        }
        return new GridInsertLocation(myContainer, getRow(), getColumn(), GridInsertMode.RowBefore);
      }
      if (direction == Direction.LEFT || direction == Direction.RIGHT) {
        int adjCol = (myMode == GridInsertMode.ColumnAfter) ? getColumn() : getColumn()-1;
        if (direction == Direction.RIGHT && adjCol+1 < myContainer.getGridColumnCount()) {
          return new GridDropLocation(myContainer, getRow(), FormEditingUtil.adjustForGap(myContainer, adjCol+1, false, 1));
        }
        if (direction == Direction.LEFT && adjCol >= 0) {
          return new GridDropLocation(myContainer, getRow(), FormEditingUtil.adjustForGap(myContainer, adjCol, false, -1));
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
