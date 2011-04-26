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
package com.intellij.uiDesigner;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.radComponents.RadAbstractGridLayoutManager;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

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

  public static boolean isColumnEmpty(final RadContainer grid, final int columnIndex) {
    return canDeleteCell(grid, columnIndex, false) == CellStatus.Empty;
  }

  public static boolean isRowEmpty(final RadContainer grid, final int rowIndex) {
    return canDeleteCell(grid, rowIndex, true) == CellStatus.Empty;
  }

  /**
   * @param cellIndex column or row index, depending on isRow parameter; must be in the range 0..grid.get{Row|Column}Count()-1
   * @param isRow if true, row inserted, otherwise column
   * @param isBefore if true, row/column will be inserted before row/column with given index, otherwise after
   */
  public static void insertRowOrColumn(final RadContainer grid, final int cellIndex, final boolean isRow, final boolean isBefore) {
    check(grid, isRow, cellIndex);

    final RadAbstractGridLayoutManager oldLayout = grid.getGridLayoutManager();

    int beforeIndex = cellIndex;
    if (!isBefore) {
      // beforeIndex can actually be equal to get{Row|Column}Count an case we add row after the last row/column
      beforeIndex++;
    }

    final LayoutManager newLayout = oldLayout.copyLayout(grid.getLayout(), isRow ? 1 : 0, isRow ? 0 : 1);
    GridConstraints[] oldConstraints = copyConstraints(grid);

    for (int i=grid.getComponentCount() - 1; i >= 0; i--){
      final GridConstraints constraints = grid.getComponent(i).getConstraints();
      adjustConstraintsOnInsert(constraints, isRow, beforeIndex, 1);
    }

    grid.setLayout(newLayout);
    fireAllConstraintsChanged(grid, oldConstraints);
  }

  private static GridConstraints[] copyConstraints(RadContainer grid) {
    final GridConstraints[] gridConstraints = new GridConstraints[grid.getComponentCount()];
    for (int i = 0; i < grid.getComponentCount(); i++) {
      gridConstraints [i] = (GridConstraints) grid.getComponent(i).getConstraints().clone();
    }
    return gridConstraints;
  }

  private static void fireAllConstraintsChanged(RadContainer grid, GridConstraints[] oldConstraints) {
    for(int i=grid.getComponentCount()-1; i >= 0; i--) {
      grid.getComponent(i).fireConstraintsChanged(oldConstraints [i]);
    }
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
  public static void splitCell(final RadContainer grid, final int cellIndex, final boolean isRow) {
    check(grid, isRow, cellIndex);

    int insertedCells = grid.getGridLayoutManager().insertGridCells(grid, cellIndex, isRow, false, false);

    for (int i=grid.getComponentCount() - 1; i >= 0; i--){
      final RadComponent component = grid.getComponent(i);
      final GridConstraints constraints = component.getConstraints();

      if (constraints.getCell(isRow) + constraints.getSpan(isRow) - 1 == cellIndex) {
        // component belongs to the cell being resized - increment component's span
        GridConstraints oldConstraints = (GridConstraints)constraints.clone();
        constraints.setSpan(isRow, constraints.getSpan(isRow) + insertedCells);
        component.fireConstraintsChanged(oldConstraints);
      }
    }
  }

  public enum CellStatus {
    Empty, Redundant, CanShift, Required
  }

  /**
   * @param cellIndex column or row index, depending on isRow parameter; must be in the range 0..grid.get{Row|Column}Count()-1
   * @param isRow if true, row is deleted, otherwise column
   * @return whether the specified column can be deleted
   */
 public static CellStatus canDeleteCell(@NotNull final RadContainer grid, final int cellIndex, final boolean isRow) {
    check(grid, isRow, cellIndex);

    // Do not allow to delete the single row/column
    if(isRow && grid.getGridRowCount() <= grid.getGridLayoutManager().getMinCellCount()) {
      return CellStatus.Required;
    }
    else if(!isRow && grid.getGridColumnCount() <= grid.getGridLayoutManager().getMinCellCount()) {
      return CellStatus.Required;
    }

    boolean haveComponents = false;
    boolean haveOrigins = false;
    boolean haveSingleSpan = false;
    for (int i = 0; i < grid.getComponentCount(); i++) {
      final GridConstraints constraints = grid.getComponent(i).getConstraints();
      final int cell = constraints.getCell(isRow);
      final int span = constraints.getSpan(isRow);

      if (cellIndex >= cell && cellIndex < cell+span) {
        haveComponents = true;
        if (cellIndex == cell) {
          haveOrigins = true;
          if (span == 1) {
            haveSingleSpan = true;
          }
        }
      }
    }
    if (haveSingleSpan)
      return CellStatus.Required;
    if (haveOrigins)
      return CellStatus.CanShift;
    if (haveComponents)
      return CellStatus.Redundant;
    return CellStatus.Empty;
  }

  public static boolean canDeleteCells(final RadContainer grid, final int[] cells, final boolean row) {
    // for multiple cells, we can't determine if deleting all cells will have a correct result
    for(int cell: cells) {
      CellStatus status = canDeleteCell(grid, cell, row);
      if (status != CellStatus.Empty) {
        if (cells.length == 1 && status == CellStatus.Redundant) {
          return true;
        }
        return false;
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
    if (canDeleteCell(grid, cellIndex, isRow) == CellStatus.Required) {
      throw new IllegalArgumentException("cell cannot be deleted");
    }

    final RadAbstractGridLayoutManager oldLayout = grid.getGridLayoutManager();

    final LayoutManager newLayout = oldLayout.copyLayout(grid.getLayout(), isRow ? -1 : 0, isRow ? 0 : -1);
    GridConstraints[] oldConstraints = copyConstraints(grid);

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
    fireAllConstraintsChanged(grid, oldConstraints);
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
    if (cellIndex == 0 && cellCount == 0) return;
    if (cellIndex < 0 || cellIndex >= cellCount) {
      throw new IllegalArgumentException("invalid index: " + cellIndex);
    }
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
      GridConstraints oldConstraints = (GridConstraints) constraints.clone();
      final int aCell = constraints.getCell(isRow);
      if (aCell == sourceCell) {
        constraints.setCell(isRow, targetCell);
      }
      else if (aCell >= startCell && aCell < endCell) {
        constraints.setCell(isRow, aCell + delta);
      }
      c.fireConstraintsChanged(oldConstraints);
    }
  }
}
