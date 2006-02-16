package com.intellij.uiDesigner.designSurface;

import com.intellij.uiDesigner.radComponents.RadContainer;

import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 09.11.2005
 * Time: 14:41:14
 * To change this template use File | Settings | File Templates.
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

  public boolean isColumnInsert() {
    return getMode() == GridInsertMode.ColumnAfter || getMode() == GridInsertMode.ColumnBefore;
  }

  public boolean isRowInsert() {
    return getMode() == GridInsertMode.RowAfter || getMode() == GridInsertMode.RowBefore;
  }

  public boolean isInsert() {
    return isColumnInsert() || isRowInsert();
  }

  public Point getTargetPoint() {
    return myTargetPoint;
  }

  public void rejectDrop() {
    myMode = GridInsertMode.NoDrop;
  }
}
