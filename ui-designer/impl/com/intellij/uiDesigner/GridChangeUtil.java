package com.intellij.uiDesigner;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.NotNull;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class GridChangeUtil {
  private GridChangeUtil() {
  }

  public static void splitColumn(final RadContainer grid, final int columnIndex) {
    splitCell(grid, columnIndex, false);
  }

  public static void splitRow(final RadContainer grid, final int rowIndex) {
    splitCell(grid, rowIndex, true);
  }

  public static boolean canDeleteColumn(final RadContainer grid, final int columnIndex) {
    return canDeleteCell(grid, columnIndex, false, false);
  }

  public static boolean isColumnEmpty(final RadContainer grid, final int columnIndex) {
    return canDeleteCell(grid, columnIndex, false, true);
  }

  public static boolean canDeleteRow(final RadContainer grid, final int rowIndex) {
    return canDeleteCell(grid, rowIndex, true, false);
  }

  public static boolean isRowEmpty(final RadContainer grid, final int rowIndex) {
    return canDeleteCell(grid, rowIndex, true, true);
  }

  /**
   * @param cellIndex column or row index, depending on isRow parameter; must be in the range 0..grid.get{Row|Column}Count()-1
   * @param isRow if true, row inserted, otherwise column
   * @param isBefore if true, row/column will be inserted before row/column with given index, otherwise after
   */
  public static void insertRowOrColumn(final RadContainer grid, final int cellIndex, final boolean isRow, final boolean isBefore) {
    check(grid, isRow, cellIndex);

    final GridLayoutManager oldLayout = (GridLayoutManager)grid.getLayout();

    int beforeIndex = cellIndex;
    if (!isBefore) {
      // beforeIndex can actually be equal to get{Row|Column}Count an case we add row after the last row/column
      beforeIndex++;
    }

    final GridLayoutManager newLayout = copyLayout(oldLayout, isRow ? 1 : 0, isRow ? 0 : 1);

    for (int i=grid.getComponentCount() - 1; i >= 0; i--){
      final GridConstraints constraints = grid.getComponent(i).getConstraints();
      adjustConstraintsOnInsert(constraints, isRow, beforeIndex, 1);
    }

    grid.setLayout(newLayout);
  }

  public static void adjustConstraintsOnInsert(final GridConstraints constraints, final boolean isRow, final int beforeIndex,
                                               final int count) {
    if (constraints.getCell(isRow) >= beforeIndex) {
      addToCell(constraints, isRow, count);
    }
    else if (isCellInsideComponent(constraints, isRow, beforeIndex)) {
      // component belongs to the cell being resized - increment component's span
      addToSpan(constraints, isRow, count);
    }
  }

  /**
   * @param cellIndex column or row index, depending on isRow parameter; must be in the range 0..grid.get{Row|Column}Count()-1
   * @param isRow if true, row is splitted, otherwise column
   */
  private static void splitCell(final RadContainer grid, final int cellIndex, final boolean isRow) {
    check(grid, isRow, cellIndex);

    final GridLayoutManager oldLayout = (GridLayoutManager)grid.getLayout();

    final GridLayoutManager newLayout = copyLayout(oldLayout, isRow ? 1 : 0, isRow ? 0 : 1);

    for (int i=grid.getComponentCount() - 1; i >= 0; i--){
      final GridConstraints constraints = grid.getComponent(i).getConstraints();

      if (constraints.getCell(isRow) > cellIndex) {
        // component starts after the cell being splitted - move it
        addToCell(constraints, isRow, 1);
      }
      else if (isCellInsideComponent(constraints, isRow, cellIndex)) {
        // component belongs to the cell being resized - increment component's span
        addToSpan(constraints, isRow, 1);
      }
    }

    grid.setLayout(newLayout);
  }

  /**
   * @param cellIndex column or row index, depending on isRow parameter; must be in the range 0..grid.get{Row|Column}Count()-1
   * @param isRow if true, row is deleted, otherwise column
   * @param mustBeEmpty
   * @return true if specified row/column can be deleted
   */
 public static boolean canDeleteCell(final RadContainer grid, final int cellIndex, final boolean isRow, final boolean mustBeEmpty) {
    check(grid, isRow, cellIndex);

    // Do not allow to delete the single row/column
    if(isRow && grid.getGridRowCount() < 2){
      return false;
    }
    else if(!isRow && grid.getGridColumnCount() < 2){
      return false;
    }

    for (int i = 0; i < grid.getComponentCount(); i++) {
      final GridConstraints constraints = grid.getComponent(i).getConstraints();
      final int cell = constraints.getCell(isRow);
      final int span = constraints.getSpan(isRow);

      if (mustBeEmpty) {
        if (cellIndex >= cell && cellIndex < cell+span) {
          return false;
        }
      }
      else {
        if (cell == cellIndex && span == 1) {
          // only cells where components with span 1 are located cannot be deleted
          return false;
        }
      }
    }

    return true;
  }

  /**
   * @param cellIndex column or row index, depending on isRow parameter; must be in the range 0..grid.get{Row|Column}Count()-1
   * @param isRow if true, row is deleted, otherwise column
   */
  public static void deleteCell(final RadContainer grid, final int cellIndex, final boolean isRow) {
    check(grid, isRow, cellIndex);
    if (!canDeleteCell(grid, cellIndex, isRow, false)) {
      throw new IllegalArgumentException("cell cannot be deleted");
    }

    final GridLayoutManager oldLayout = (GridLayoutManager)grid.getLayout();

    final GridLayoutManager newLayout = copyLayout(oldLayout, isRow ? -1 : 0, isRow ? 0 : -1);

    for (int i=grid.getComponentCount() - 1; i >= 0; i--){
      final GridConstraints constraints = grid.getComponent(i).getConstraints();

      if (constraints.getCell(isRow) > cellIndex) {
        // component starts after the cell being deleted - move it
        addToCell(constraints, isRow, -1);
      }
      else if (isCellInsideComponent(constraints, isRow, cellIndex)) {
        // component belongs to the cell being deleted - decrement component's span
        addToSpan(constraints, isRow, -1);
      }
    }

    grid.setLayout(newLayout);
  }


  private static boolean isCellInsideComponent(final GridConstraints constraints, final boolean isRow, final int cellIndex) {
    final int cell = constraints.getCell(isRow);
    final int span = constraints.getSpan(isRow);
    return cell <= cellIndex && cellIndex <= cell + span - 1;
  }

  /**
   * check whether passed container is grid and cellIndex is in proper range
   */
  private static void check(@NotNull RadContainer grid, final boolean isRow, final int cellIndex){
    if (!grid.getLayoutManager().isGrid()){
      throw new IllegalArgumentException("container must be grid");
    }

    final int cellCount = isRow ? grid.getGridRowCount() : grid.getGridColumnCount();
    if (cellIndex < 0 || cellIndex >= cellCount) {
      throw new IllegalArgumentException("invalid index: " + cellIndex);
    }
  }

  private static GridLayoutManager copyLayout(final GridLayoutManager oldLayout, final int rowDelta, final int columnDelta){
    final GridLayoutManager newLayout = new GridLayoutManager(oldLayout.getRowCount() + rowDelta, oldLayout.getColumnCount() + columnDelta);
    newLayout.setMargin(oldLayout.getMargin());
    newLayout.setHGap(oldLayout.getHGap());
    newLayout.setVGap(oldLayout.getVGap());
    return newLayout;
  }

  private static void addToCell(final GridConstraints constraints, final boolean isRow, final int delta){
    if (isRow) {
      constraints.setRow(constraints.getRow() + delta);
    }
    else {
      constraints.setColumn(constraints.getColumn() + delta);
    }
  }

  private static void addToSpan(final GridConstraints constraints, final boolean isRow, final int delta){
    if (isRow) {
      constraints.setRowSpan(constraints.getRowSpan() + delta);
    }
    else {
      constraints.setColSpan(constraints.getColSpan() + delta);
    }
  }

  public static void moveCells(final RadContainer container, final boolean isRow, final int[] cellsToMove, int targetCell) {
    for(int i=0; i<cellsToMove.length; i++) {
      final int sourceCell = cellsToMove[i];
      moveCell(container, isRow, sourceCell, targetCell);
      if (sourceCell < targetCell) {
        for(int j=i+1; j<cellsToMove.length; j++) {
          cellsToMove [j]--;
        }
      }
      else {
        targetCell++;
      }
    }
  }

  public static void moveCell(final RadContainer container, final boolean isRow, final int sourceCell, int targetCell) {
    if (targetCell == sourceCell || targetCell == sourceCell+1) return;
    // if column moved to left - components inbetween move to right, and vice versa
    int delta = (sourceCell > targetCell) ? 1 : -1;
    int startCell = Math.min(sourceCell, targetCell);
    int endCell = Math.max(sourceCell, targetCell);
    if (sourceCell < targetCell) targetCell--;
    for(RadComponent c: container.getComponents()) {
      GridConstraints constraints = c.getConstraints();
      final int aCell = constraints.getCell(isRow);
      if (aCell == sourceCell) {
        constraints.setCell(isRow, targetCell);
      }
      else if (aCell >= startCell && aCell < endCell) {
        constraints.setCell(isRow, aCell + delta);
      }
    }
  }
}
