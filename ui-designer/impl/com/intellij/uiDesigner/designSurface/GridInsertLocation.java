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
class GridInsertLocation {
  private RadContainer myContainer;
  private int myRow;
  private int myColumn;
  private Rectangle myCellRect;
  private GridInsertMode myMode;

  public GridInsertLocation(final GridInsertMode mode) {
    myMode = mode;
  }

  public GridInsertLocation(final RadContainer container,
                            final int row,
                            final int column,
                            final Rectangle cellRect,
                            final GridInsertMode mode) {
    myContainer = container;
    myRow = row;
    myColumn = column;
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
}
