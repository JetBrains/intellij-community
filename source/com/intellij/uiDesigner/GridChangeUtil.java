package com.intellij.uiDesigner;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class GridChangeUtil {
  public static void insertRowBefore(final RadContainer grid, final int rowIndex) {
    insertRowOrColumn(grid, rowIndex, true, true);
  }

  public static void insertRowAfter(final RadContainer grid, final int rowIndex) {
    insertRowOrColumn(grid, rowIndex, true, false);
  }

  public static void insertColumnBefore(final RadContainer grid, final int columnIndex) {
    insertRowOrColumn(grid, columnIndex, false, true);
  }

  public static void insertColumnAfter(final RadContainer grid, final int columnIndex) {
    insertRowOrColumn(grid, columnIndex, false, false);
  }

  public static void splitColumn(final RadContainer grid, final int columnIndex) {
    splitCell(grid, columnIndex, false);
  }

  public static void splitRow(final RadContainer grid, final int rowIndex) {
    splitCell(grid, rowIndex, true);
  }

  public static boolean canDeleteColumn(final RadContainer grid, final int columnIndex) {
    return canDeleteCell(grid, columnIndex, false);
  }

  public static void deleteColumn(final RadContainer grid, final int columnIndex) {
    deleteCell(grid, columnIndex, false);
  }

  public static boolean canDeleteRow(final RadContainer grid, final int rowIndex) {
    return canDeleteCell(grid, rowIndex, true);
  }

  public static void deleteRow(final RadContainer grid, final int rowIndex) {
    deleteCell(grid, rowIndex, true);
  }
  
  /**
   * @param cellIndex column or row index, depending on isRow parameter; must be in the range 0..grid.get{Row|Column}Count()-1
   * @param isRow if true, row inserted, otherwise column  
   * @param isBefore if true, row/column will be inserted before row/column with given index, otherwise after   
   */ 
  private static void insertRowOrColumn(final RadContainer grid, final int cellIndex, final boolean isRow, final boolean isBefore) {
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

      if (getCell(constraints, isRow) >= beforeIndex) {
        addToCell(constraints, isRow, 1);
      }
      else if (isCellInsideComponent(constraints, isRow, beforeIndex)) {
        // component belongs to the cell being resized - increment component's span
        addToSpan(constraints, isRow, 1);
      }
    }
    
    grid.setLayout(newLayout);
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

      if (getCell(constraints, isRow) > cellIndex) {
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
   * @return true if specified row/column can be deleted 
   */  
  private static boolean canDeleteCell(final RadContainer grid, final int cellIndex, final boolean isRow) {
    check(grid, isRow, cellIndex);

    // Do not allow to delete the single row/column
    final GridLayoutManager layout = (GridLayoutManager)grid.getLayout();
    if(isRow && layout.getRowCount() < 2){
      return false;
    }
    else if(!isRow && layout.getColumnCount() < 2){
      return false;
    }

    for (int i = 0; i < grid.getComponentCount(); i++) {
      final GridConstraints constraints = grid.getComponent(i).getConstraints();
      final int cell = getCell(constraints, isRow);
      final int span = getSpan(constraints, isRow);

      if (cell == cellIndex && span == 1) {
        // only cells where components with span 1 are located cannot be deleted  
        return false;
      }
    }

    return true;
  }
  
  /**
   * @param cellIndex column or row index, depending on isRow parameter; must be in the range 0..grid.get{Row|Column}Count()-1
   * @param isRow if true, row is deleted, otherwise column
   */  
  private static void deleteCell(final RadContainer grid, final int cellIndex, final boolean isRow) {
    check(grid, isRow, cellIndex);
    if (!canDeleteCell(grid, cellIndex, isRow)) {
      throw new IllegalArgumentException("cell cannot be deleted");
    }
    
    final GridLayoutManager oldLayout = (GridLayoutManager)grid.getLayout();

    final GridLayoutManager newLayout = copyLayout(oldLayout, isRow ? -1 : 0, isRow ? 0 : -1);

    for (int i=grid.getComponentCount() - 1; i >= 0; i--){
      final GridConstraints constraints = grid.getComponent(i).getConstraints();

      if (getCell(constraints, isRow) > cellIndex) {
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
    final int cell = getCell(constraints, isRow);
    final int span = getSpan(constraints, isRow);
    return cell <= cellIndex && cellIndex <= cell + span - 1;
  }
  
  /**
   * check whether passed container is grid and cellIndex is in proper range
   */ 
  private static void check(final RadContainer grid, final boolean isRow, final int cellIndex){
    if (grid == null){
      throw new IllegalArgumentException("grid cannot be null");
    }
    if (!grid.isGrid()){
      throw new IllegalArgumentException("container must be grid");
    }

    final GridLayoutManager layout = (GridLayoutManager)grid.getLayout();

    final int cellCount = isRow ? layout.getRowCount() : layout.getColumnCount();
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

  private static int getSpan(final GridConstraints constraints, final boolean isRow){
    return isRow ? constraints.getRowSpan() : constraints.getColSpan();
  }

  private static int getCell(final GridConstraints constraints, final boolean isRow){
    return isRow ? constraints.getRow() : constraints.getColumn();
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
}
