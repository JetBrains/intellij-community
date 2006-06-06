/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.util.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Arrays;

public class ListTableModel <Item> extends TableViewModel<Item> implements ItemRemovable {
  private ColumnInfo[] myColumnInfos;
  private List<Item> myItems;
  private int mySortByColumn;
  private int mySortingType = SortableColumnModel.SORT_ASCENDING;

  private boolean myIsSortable = true;

  public ListTableModel(ColumnInfo[] columnInfos) {
    this(columnInfos, new ArrayList<Item>(), 0);
  }

  public ListTableModel(ColumnInfo[] columnNames, List<Item> tests, int selectedColumn) {
    myColumnInfos = columnNames;
    myItems = tests;
    mySortByColumn = selectedColumn;
    setSortable(true);
  }

  public boolean isCellEditable(int rowIndex, int columnIndex) {
    return myColumnInfos[columnIndex].isCellEditable(myItems.get(rowIndex));
  }

  public Class getColumnClass(int columnIndex) {
    return myColumnInfos[columnIndex].getColumnClass();
  }

  public ColumnInfo[] getColumnInfos() {
    return myColumnInfos;
  }

  public String getColumnName(int column) {
    return myColumnInfos[column].getName();
  }

  public int getRowCount() {
    return myItems.size();
  }

  public int getColumnCount() {
    return myColumnInfos.length;
  }

  public void setItems(List<Item> items) {
    myItems = items;
    fireTableDataChanged();
    resort();
  }

  public Object getValueAt(int rowIndex, int columnIndex) {
    return myColumnInfos[columnIndex].valueOf(myItems.get(rowIndex));
  }

  public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
    if (rowIndex < myItems.size()) {
      myColumnInfos[columnIndex].setValue(myItems.get(rowIndex), aValue);
    }
  }

  /**
   * true if changed
   * @param columnInfos
   * @return
   */
  public boolean setColumnInfos(final ColumnInfo[] columnInfos) {
    if (myColumnInfos != null && !Arrays.equals(columnInfos, myColumnInfos)) {
      return false;
    }

    myColumnInfos = columnInfos;
    fireTableStructureChanged();
    return true;
  }

  public List<Item> getItems() {
    return Collections.unmodifiableList(myItems);
  }

  public void sortByColumn(int columnIndex) {
    if (mySortByColumn != columnIndex) {
      mySortingType = SortableColumnModel.SORT_ASCENDING;
    }
    else {
      switchSorting();
    }
    mySortByColumn = columnIndex;
    resort();
  }

  public void sortByColumn(int columnIndex, int sortingType) {
    mySortByColumn = columnIndex;
    mySortingType = sortingType;
    resort();
  }

  private void switchSorting() {
    if (mySortingType == SortableColumnModel.SORT_ASCENDING) {
      mySortingType = SortableColumnModel.SORT_DESCENDING;
    }
    else {
      mySortingType = SortableColumnModel.SORT_ASCENDING;
    }
  }

  protected Object getAspectOf(int aspectIndex, Object item) {
    return myColumnInfos[aspectIndex].valueOf(item);
  }

  private void resort() {
    if (myIsSortable) {
      final ColumnInfo columnInfo = myColumnInfos[mySortByColumn];
      if (columnInfo.isSortable()) {
        columnInfo.sort(myItems);
        if (mySortingType == SortableColumnModel.SORT_DESCENDING) {
          Collections.reverse(myItems);
        }
        fireTableDataChanged();
      }
    }

  }

  public int getSortedColumnIndex() {
    return mySortByColumn;
  }

  public int getSortingType() {
    return mySortingType;
  }

  public void setSortable(boolean aBoolean) {
    myIsSortable = aBoolean;
  }

  public boolean isSortable() {
    return myIsSortable;
  }

  public int indexOf(Item item) {
    return myItems.indexOf(item);
  }

  public void removeRow(int idx) {
    myItems.remove(idx);
    fireTableRowsDeleted(idx, idx);
  }
}
