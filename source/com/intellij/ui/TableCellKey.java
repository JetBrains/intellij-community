package com.intellij.ui;

/**
 * (rowIndex, columnIndex) is a unique key to identify cell inside JTable
 */
final class TableCellKey{
  public final int myRowIndex;
  public final int myColumnIndex;

  public TableCellKey(int rowIndex, int columnIndex) {
    myRowIndex = rowIndex;
    myColumnIndex = columnIndex;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TableCellKey)) return false;

    final TableCellKey myKey = (TableCellKey)o;

    if (myColumnIndex != myKey.myColumnIndex) return false;
    if (myRowIndex != myKey.myRowIndex) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = myRowIndex;
    result = 29 * result + myColumnIndex;
    return result;
  }
}
