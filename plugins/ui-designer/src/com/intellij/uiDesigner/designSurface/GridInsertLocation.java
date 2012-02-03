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
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.GridChangeUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.radComponents.RadAbstractGridLayoutManager;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
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

  private boolean mySpanInsertMode;

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

  public void setSpanInsertMode(boolean spanInsertMode) {
    mySpanInsertMode = spanInsertMode;
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

  private int getInsertCell() {
    return isRowInsert() ? myRow : myColumn;
  }

  private int getOppositeCell() {
    return isRowInsert() ? myColumn : myRow;
  }

  private boolean isInsertAfter() {
    return myMode == GridInsertMode.ColumnAfter || myMode == GridInsertMode.RowAfter;
  }

  @Override public boolean canDrop(ComponentDragObject dragObject) {
    Rectangle rc = getDragObjectDimensions(dragObject, true);
    int size = isRowInsert() ? rc.width : rc.height;
    if (isInsertInsideComponent(size)) {
      LOG.debug("GridInsertLocation.canDrop()=false because insert inside component");
      return false;
    }

    // TODO[yole]: any other conditions to check here?
    LOG.debug("GridInsertLocation.canDrop()=true");
    return true;
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

  private boolean isInsertInsideComponent(final int size) {
    int endColumn = getInsertCell();
    if (isInsertAfter()) endColumn++;
    int row = getOppositeCell();

    for(int r=row; r<row+size; r++) {
      for(int col = 0; col<endColumn; col++) {
        RadComponent component;
        if (isColumnInsert()) {
          component = RadAbstractGridLayoutManager.getComponentAtGrid(getContainer(), r, col);
        }
        else {
          component = RadAbstractGridLayoutManager.getComponentAtGrid(getContainer(), col, r);
        }

        if (component != null) {
          GridConstraints constraints = component.getConstraints();
          final boolean isRow = !isColumnInsert();
          if (constraints.getCell(isRow) + constraints.getSpan(isRow) > endColumn &&
              constraints.getSpan(isRow) > 1) {
            return true;
          }
        }
      }
    }
    return false;
  }

  @Override public void placeFeedback(FeedbackLayer feedbackLayer, ComponentDragObject dragObject) {
    final int insertCol = getColumn();
    final int insertRow = getRow();

    Rectangle feedbackRect = getGridFeedbackRect(dragObject, isColumnInsert(), isRowInsert(), false);
    if (feedbackRect == null) {
      feedbackLayer.removeFeedback();
      return;
    }
    Rectangle cellRect = getGridFeedbackCellRect(dragObject, isColumnInsert(), isRowInsert(), false);
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
      int lastColIndex = insertCol + dragObject.getColSpan(0);
      if (lastColIndex > vGridLines.length - 1) {
        lastColIndex = insertCol + 1;
      }
      
      int lastRowIndex = insertRow + dragObject.getRowSpan(0);
      if (lastRowIndex > hGridLines.length - 1) {
        lastRowIndex = insertRow + 1;
      }
      
      int cellWidth = vGridLines [lastColIndex] - vGridLines [insertCol];
      int cellHeight = hGridLines [lastRowIndex] - hGridLines [insertRow];
      RadComponent component = layoutManager.getComponentAtGrid(getContainer(), insertRow, insertCol);
      if (component != null && mySpanInsertMode) {
        Rectangle bounds = component.getBounds();
        bounds.translate(-vGridLines [insertCol], -hGridLines [insertRow]);

        int spaceToRight = vGridLines [lastColIndex] - vGridLines [insertCol] - (bounds.x + bounds.width);
        int spaceBelow = hGridLines [lastRowIndex] - hGridLines [insertRow] - (bounds.y + bounds.height);
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
          boolean spanInsertMode = false;
          
          if (isRowInsert()) {
            int columns = layoutManager.getGridColumnCount(getContainer());
            for (int i = 0; i < columns; i++) {
              if (i != insertCol && RadAbstractGridLayoutManager.getComponentAtGrid(getContainer(), insertRow, i) != null) {
                spanInsertMode = true;
                break;
              }
            }
          } else {
            int rows = layoutManager.getGridRowCount(getContainer());
            for (int i = 0; i < rows; i++) {
              if (i != insertRow && RadAbstractGridLayoutManager.getComponentAtGrid(getContainer(), i, insertCol) != null) {
                spanInsertMode = true;
                break;
              }
            }
          }

          if (!spanInsertMode) {
            rcFeedback = null;
          }
        }

        if (rcFeedback != null) {
          rcFeedback.translate(vGridLines [insertCol], hGridLines [insertRow]);
        }
      }

      if (rcFeedback == null) {
        if (insertCol == layoutManager.getGridColumnCount(getContainer())-1 && myMode == GridInsertMode.ColumnAfter) {
          final Dimension initialSize = dragObject.getInitialSize(getContainer());
          int feedbackX = vGridLines [vGridLines.length-1] + layoutManager.getGapCellSize(myContainer, false);
          int remainingSize = getContainer().getDelegee().getWidth() - feedbackX;
          if (!dragObject.isHGrow() && remainingSize > initialSize.width) {
           if (dragObject.isVGrow() || initialSize.height > cellHeight) {
              rcFeedback = new Rectangle(feedbackX, hGridLines [insertRow], initialSize.width, cellHeight);
            }
            else {
              rcFeedback = new Rectangle(feedbackX, hGridLines [insertRow] + (cellHeight - initialSize.height)/2,
                                         initialSize.width, initialSize.height);
            }
          }
          else if (remainingSize >= 4) {
            rcFeedback = new Rectangle(feedbackX, hGridLines [insertRow], remainingSize, cellHeight);
          }
        }
        else if (insertRow == layoutManager.getGridRowCount(getContainer())-1 && myMode == GridInsertMode.RowAfter) {
          final Dimension initialSize = dragObject.getInitialSize(getContainer());
          int feedbackY = hGridLines [hGridLines.length-1] + layoutManager.getGapCellSize(myContainer, true);
          int remainingSize = getContainer().getDelegee().getHeight() - feedbackY;
          if (!dragObject.isVGrow() && remainingSize > initialSize.height) {
            rcFeedback = new Rectangle(vGridLines [insertCol], feedbackY, cellWidth, initialSize.height);
          }
          else if (remainingSize >= 4) {
            rcFeedback = new Rectangle(vGridLines [insertCol], feedbackY, cellWidth, remainingSize);
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
    int displayRow = myRow + getContainer().getGridLayoutManager().getCellIndexBase();
    int displayColumn = myColumn + getContainer().getGridLayoutManager().getCellIndexBase();
    String displayName = getContainer().getDisplayName();
    switch(myMode) {
      case ColumnBefore: return UIDesignerBundle.message("insert.feedback.before.col", displayName, displayRow, displayColumn);
      case ColumnAfter:  return UIDesignerBundle.message("insert.feedback.after.col", displayName, displayRow, displayColumn);
      case RowBefore:    return UIDesignerBundle.message("insert.feedback.before.row", displayName, displayColumn, displayRow);
      case RowAfter:     return UIDesignerBundle.message("insert.feedback.after.row", displayName, displayColumn, displayRow);
    }
    return null;
  }

  public static Rectangle getInsertFeedbackPosition(final GridInsertMode mode, final RadContainer container, final Rectangle cellRect,
                                                    final Rectangle feedbackRect) {
    final RadAbstractGridLayoutManager manager = container.getGridLayoutManager();
    int[] vGridLines = manager.getVerticalGridLines(container);
    int[] hGridLines = manager.getHorizontalGridLines(container);

    Rectangle rc = feedbackRect;
    int w=4;
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
    RadContainer container = getContainer();
    boolean canGrow = isRowInsert() ? dragObject.isVGrow() : dragObject.isHGrow();
    int cell = isRowInsert() ? getRow() : getColumn();

    int cellsToInsert = 1;
    if (components.length > 0) {
      int cellSize = container.getGridCellCount(isRowInsert());
        Rectangle rc = getDragObjectDimensions(dragObject, cell < cellSize - 1);
        int size = isRowInsert() ? rc.height : rc.width;
        if (size > 0) {
          cellsToInsert = size;
      }
    }

    GridSpanInsertProcessor spanInsertProcessor =
      mySpanInsertMode && dragObject.getComponentCount() == 1 ? new GridSpanInsertProcessor(container, getRow(), getColumn(), myMode,
                                                                                            dragObject) : null;

    int newCell = insertGridCells(container, cell, cellsToInsert, canGrow, isRowInsert(), !isInsertAfter(), constraintsToAdjust);
    if (isRowInsert()) {
      row = newCell;
    }
    else {
      col = newCell;
    }

    if (components.length > 0) {
      if (spanInsertProcessor != null) {
        spanInsertProcessor.doBefore(newCell);
      }

      dropIntoGrid(container, components, row, col, dragObject);

      if (spanInsertProcessor != null) {
        spanInsertProcessor.doAfter(newCell);
      }
    }
  }

  private static int insertGridCells(RadContainer container, int cell, int cellsToInsert, boolean canGrow, boolean isRow, boolean isBefore,
                                     GridConstraints[] constraintsToAdjust) {
    int insertedCells = 1;
    for(int i=0; i<cellsToInsert; i++) {
      insertedCells = container.getGridLayoutManager().insertGridCells(container, cell, isRow, isBefore, canGrow);
    }
    // for insert after, shift only by one cell + possibly one gap cell, not by entire number of insertions
    if (!isBefore) {
      cell += insertedCells;
    }
    checkAdjustConstraints(constraintsToAdjust, isRow, cell, insertedCells);
    return cell;
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
  public ComponentDropLocation getAdjacentLocation(Direction direction) {
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

  private ComponentDropLocation getLocationAtParent(final Direction direction) {
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
